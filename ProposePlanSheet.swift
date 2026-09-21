//
//  ProposePlanSheet.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 8/27/26.
//

import SwiftUI
import CoreLocation

/// The single plan-creation screen in the app. Five triggers present it:
///   - Matches → connected match row → calendar icon           (.proposal)
///   - Matches → row → MatchDetailView → "Propose a Plan"       (.proposal)
///   - Messages → thread → calendar icon                        (.proposal)
///   - Today → "+"                                              (.openPost)
///   - Upcoming → "+"                                           (.groupInvite)
///
/// .proposal writes a Plan (via PlansService, through MessagingViewModel.proposePlan) and posts
/// a planProposal message into the thread. .openPost writes a TodayPlan to todayPlans — no
/// recipient, claimable first-come-first-served. .groupInvite writes a GroupPlan to groupPlans —
/// no message, no thread, invitees found by opening Upcoming. Everything about the view is
/// shared by default; a mode only changes something explicitly called out below.
struct ProposePlanSheet: View {
    enum Mode {
        case proposal(matchID: String, receiverID: String)
        case openPost
        case groupInvite
    }

    @Environment(\.dismiss) private var dismiss

    let mode: Mode
    private let messagingViewModel: MessagingViewModel?
    private let todayViewModel: TodayViewModel?

    /// From a connected match or an existing thread — writes a Plan + posts a chat message.
    init(matchID: String, receiverID: String, viewModel: MessagingViewModel) {
        self.mode = .proposal(matchID: matchID, receiverID: receiverID)
        self.messagingViewModel = viewModel
        self.todayViewModel = nil
        _proposedDate = State(initialValue: Self.nextHourRoundedUp())
    }

    /// Today's open broadcast — writes a TodayPlan, no recipient.
    init(todayViewModel: TodayViewModel) {
        self.mode = .openPost
        self.messagingViewModel = nil
        self.todayViewModel = todayViewModel
        let choice = Self.defaultDayChoice()
        _dayChoice = State(initialValue: choice)
        _proposedDate = State(initialValue: Self.defaultPickerTime(for: choice))
    }

    /// Group invite from Upcoming — writes a GroupPlan directly. No message, no thread.
    init() {
        self.mode = .groupInvite
        self.messagingViewModel = nil
        self.todayViewModel = nil
        _proposedDate = State(initialValue: Self.nextHourRoundedUp())
    }

    /// openPost's When segment — proposal and groupInvite never touch this.
    enum DayChoice: String, CaseIterable {
        case today = "Today"
        case tomorrow = "Tomorrow"
    }

    /// A mutual match the host can invite, with just enough profile info to render a
    /// checkmark row (photo + name).
    private struct MutualMatchOption: Identifiable {
        let id: String
        let displayName: String
        let photoURL: String?
    }

    // ── form state ─────────────────────────────────────────────────────────
    @State private var activityName: String = ""
    @State private var dayChoice: DayChoice = .today
    @State private var proposedDate: Date

    @State private var isSubmitting = false
    @State private var errorMessage: String?

    // ── activity search ─────────────────────────────────────────────────────
    @State private var availableActivities: [Activity] = []
    @State private var filteredActivities: [Activity] = []
    @State private var showSuggestions = false

    // ── location search ─────────────────────────────────────────────────────
    @State private var location: String = ""
    @State private var selectedLocationName: String?
    @State private var selectedLocationCoordinate: CLLocationCoordinate2D?
    @State private var userCoordinate: CLLocationCoordinate2D?

    // ── groupInvite: mutual match checklist ──────────────────────────────────
    @State private var mutualMatchOptions: [MutualMatchOption] = []
    @State private var selectedInviteeIDs: Set<String> = []
    @State private var isLoadingMatches = false

    private let authService = FirebaseAuthService.shared

    private var navTitle: String {
        switch mode {
        case .proposal: return "Propose a Plan"
        case .openPost: return "Post a Plan"
        case .groupInvite: return "Plan Something"
        }
    }

    private var headerText: String {
        switch mode {
        case .proposal: return "What do you want to do?"
        case .openPost: return "Open to everyone on ATX Friends · next 24 hours"
        case .groupInvite: return "Invite matches — any date."
        }
    }

    private var submitIcon: String {
        switch mode {
        case .proposal: return "calendar.badge.plus"
        case .openPost: return "paperplane.fill"
        case .groupInvite: return "paperplane.fill"
        }
    }

    private var submitLabel: String {
        switch mode {
        case .proposal: return "Send Proposal"
        case .openPost: return "Post Plan"
        case .groupInvite: return "Send Invites"
        }
    }

    var canSubmit: Bool {
        guard
            !activityName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            !location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            !isSubmitting
        else { return false }

        if case .groupInvite = mode {
            return !selectedInviteeIDs.isEmpty
        }
        return true
    }

    /// True once it's too late in the day for a 15-minute-out "Today" slot to exist.
    private var todayIsAvailable: Bool { Self.todayIsAvailable() }

    /// openPost's selectable range for the currently-chosen day segment.
    private var selectableRange: ClosedRange<Date> {
        switch dayChoice {
        case .today: return Self.todayRange()
        case .tomorrow: return Self.tomorrowRange()
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                ScrollViewReader { scrollProxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // ── header blurb ─────────────────────────────────
                        Text(headerText)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appNavy)
                            .padding(.top, 8)

                        // ── activity field ───────────────────────────────
                        VStack(alignment: .leading, spacing: 8) {
                            Label("Activity", systemImage: "figure.walk")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)

                            TextField("e.g. Hiking, Tacos, Board games…", text: $activityName)
                                .font(.system(size: 16, design: .rounded))
                                .textFieldStyle(.plain)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(Color.white.opacity(0.85))
                                .cornerRadius(12)
                                .onChange(of: activityName) { _, newValue in
                                    filterActivities(query: newValue)
                                }

                            // Suggestion chips
                            if showSuggestions && !filteredActivities.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(filteredActivities.prefix(8)) { activity in
                                            Button {
                                                activityName = activity.name
                                                showSuggestions = false
                                            } label: {
                                                Text(activity.name)
                                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                                    .foregroundColor(Color.appPrimary)
                                                    .padding(.horizontal, 12)
                                                    .padding(.vertical, 6)
                                                    .background(Color.appPrimary.opacity(0.15))
                                                    .cornerRadius(16)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── groupInvite: who's invited ───────────────────
                        if case .groupInvite = mode {
                            matchChecklist
                        }

                        // ── date + time ──────────────────────────────────
                        VStack(alignment: .leading, spacing: 8) {
                            Label("When", systemImage: "calendar")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)

                            switch mode {
                            case .openPost:
                                // Today | Tomorrow segmented control — no month grid, just the
                                // same grey time pill that .graphical rendering shows beneath
                                // its calendar.
                                HStack {
                                    Picker("", selection: $dayChoice) {
                                        Text(DayChoice.today.rawValue)
                                            .disabled(!todayIsAvailable)
                                            .tag(DayChoice.today)
                                        Text(DayChoice.tomorrow.rawValue)
                                            .tag(DayChoice.tomorrow)
                                    }
                                    .pickerStyle(.segmented)
                                    .labelsHidden()

                                    DatePicker(
                                        "",
                                        selection: $proposedDate,
                                        in: selectableRange,
                                        displayedComponents: .hourAndMinute
                                    )
                                    .datePickerStyle(.compact)
                                    .labelsHidden()
                                    .tint(Color.appPrimary)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(Color.white.opacity(0.85))
                                .cornerRadius(12)
                                .onChange(of: dayChoice) { _, newValue in
                                    proposedDate = Self.defaultPickerTime(for: newValue)
                                }
                            case .proposal, .groupInvite:
                                DatePicker(
                                    "",
                                    selection: $proposedDate,
                                    in: Date()...,
                                    displayedComponents: [.date, .hourAndMinute]
                                )
                                .datePickerStyle(.graphical)
                                .tint(Color.appPrimary)
                                .background(Color.white.opacity(0.85))
                                .cornerRadius(12)
                            }
                        }

                        // ── location (required) ──────────────────────────
                        PlanLocationField(
                            text: $location,
                            selectedLocationName: $selectedLocationName,
                            selectedCoordinate: $selectedLocationCoordinate,
                            userCoordinate: userCoordinate,
                            scrollProxy: scrollProxy,
                            scrollAnchorID: "whereField"
                        )

                        // ── error ────────────────────────────────────────
                        if let err = errorMessage {
                            Text(err)
                                .font(.system(size: 14, design: .rounded))
                                .foregroundColor(.red)
                                .padding(.horizontal, 4)
                        }

                        // ── submit ───────────────────────────────────────
                        Button {
                            Task { await submit() }
                        } label: {
                            HStack {
                                if isSubmitting {
                                    ProgressView().tint(.white)
                                } else {
                                    Image(systemName: submitIcon)
                                    Text(submitLabel)
                                }
                            }
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(
                                canSubmit
                                ? LinearGradient(
                                    colors: [
                                        Color.appPrimary,
                                        Color.appPrimary
                                    ],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                                : LinearGradient(
                                    colors: [Color.gray.opacity(0.4), Color.gray.opacity(0.4)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(16)
                        }
                        .disabled(!canSubmit)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
                }
                }
            }
            .navigationTitle(navTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .font(.system(size: 16, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
        }
        .task {
            await loadActivities()
            await loadUserCoordinate()
            await loadMutualMatches()
        }
    }

    // MARK: - Match Checklist

    private var matchChecklist: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Who's invited?", systemImage: "person.2.fill")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimary)

            if isLoadingMatches {
                ProgressView()
                    .tint(Color.appPrimary)
            } else if mutualMatchOptions.isEmpty {
                Text("You don't have any mutual matches yet.")
                    .font(.system(size: 14, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
            } else {
                VStack(spacing: 0) {
                    ForEach(mutualMatchOptions) { option in
                        Button {
                            toggleInvitee(option.id)
                        } label: {
                            HStack(spacing: 12) {
                                if let photoURL = option.photoURL, let url = URL(string: photoURL) {
                                    AsyncImage(url: url) { image in
                                        image.resizable().scaledToFill()
                                    } placeholder: {
                                        Circle().fill(Color.appPrimary.opacity(0.3))
                                    }
                                    .frame(width: 40, height: 40)
                                    .clipShape(Circle())
                                } else {
                                    Circle()
                                        .fill(Color.appPrimary.opacity(0.3))
                                        .frame(width: 40, height: 40)
                                        .overlay {
                                            Image(systemName: "person.fill")
                                                .foregroundColor(.white)
                                        }
                                }

                                Text(option.displayName)
                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                    .foregroundColor(Color.appNavy)

                                Spacer()

                                Image(systemName: selectedInviteeIDs.contains(option.id) ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 20))
                                    .foregroundColor(selectedInviteeIDs.contains(option.id) ? Color.appPrimary : Color.gray.opacity(0.4))
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)

                        if option.id != mutualMatchOptions.last?.id {
                            Divider().padding(.leading, 14)
                        }
                    }
                }
                .background(Color.white.opacity(0.85))
                .cornerRadius(12)
            }
        }
    }

    private func toggleInvitee(_ userID: String) {
        if selectedInviteeIDs.contains(userID) {
            selectedInviteeIDs.remove(userID)
        } else {
            selectedInviteeIDs.insert(userID)
        }
    }

    // MARK: - Helpers

    private func filterActivities(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            filteredActivities = []
            showSuggestions = false
        } else {
            filteredActivities = availableActivities.filter {
                $0.name.localizedCaseInsensitiveContains(trimmed)
            }
            showSuggestions = true
        }
    }

    /// Suggestion-chip source. .proposal and .groupInvite draw from the global shared activity
    /// list. .openPost seeds from the user's own profile activities instead — the Today feed's
    /// filter chips group posts by exact activity-name match (TodayViewModel.availableActivities),
    /// so biasing toward the names the user already uses keeps posts groupable.
    private func loadActivities() async {
        switch mode {
        case .proposal, .groupInvite:
            guard let activities = try? await FirestoreService.shared.fetchActivities() else { return }
            await MainActor.run { availableActivities = activities }
        case .openPost:
            guard let userID = authService.currentUserID,
                  let user = try? await FirestoreService.shared.fetchUser(userID: userID) else { return }
            await MainActor.run { availableActivities = user.activities }
        }
    }

    /// Loads the current user's stored coordinates (the same ones the geofencing gate
    /// captured at signup) to bias location search and sort results by distance.
    private func loadUserCoordinate() async {
        guard let userID = authService.currentUserID,
              let user = try? await FirestoreService.shared.fetchUser(userID: userID) else { return }
        await MainActor.run {
            userCoordinate = CLLocationCoordinate2D(latitude: user.latitude, longitude: user.longitude)
        }
    }

    /// groupInvite's checklist source: every mutual match, with the other user's photo/name.
    private func loadMutualMatches() async {
        guard case .groupInvite = mode, let userID = authService.currentUserID else { return }
        await MainActor.run { isLoadingMatches = true }
        defer { Task { @MainActor in isLoadingMatches = false } }

        guard let matches = try? await FirestoreService.shared.fetchMatches(for: userID) else { return }
        let mutual = matches.filter { $0.isMutualMatch }

        var options: [MutualMatchOption] = []
        for match in mutual {
            guard let otherUserID = match.otherUserID(for: userID),
                  let user = try? await FirestoreService.shared.fetchUser(userID: otherUserID) else { continue }
            options.append(MutualMatchOption(id: otherUserID, displayName: user.displayName, photoURL: user.photoURLs.first))
        }
        await MainActor.run { mutualMatchOptions = options }
    }

    private func submit() async {
        let name = activityName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        let trimmedLocation = location.trimmingCharacters(in: .whitespacesAndNewlines)

        switch mode {
        case .proposal(let matchID, let receiverID):
            guard let messagingViewModel else { return }
            do {
                try await messagingViewModel.proposePlan(
                    matchID: matchID,
                    receiverID: receiverID,
                    activityName: name,
                    location: trimmedLocation.isEmpty ? nil : trimmedLocation,
                    locationName: selectedLocationName,
                    locationLatitude: selectedLocationCoordinate?.latitude,
                    locationLongitude: selectedLocationCoordinate?.longitude,
                    scheduledDate: proposedDate
                )
                dismiss()
            } catch {
                errorMessage = "Couldn't send proposal. Please try again."
            }
        case .openPost:
            guard let todayViewModel else { return }
            // The sheet may have sat open for a while — re-clamp against the live range
            // in case the chosen day/time fell out of bounds (e.g. the 15-minute minimum
            // for Today, or the rolling 24-hour ceiling for Tomorrow) since it was picked.
            let effectiveChoice = todayIsAvailable ? dayChoice : .tomorrow
            let range = effectiveChoice == .today ? Self.todayRange() : Self.tomorrowRange()
            proposedDate = min(max(proposedDate, range.lowerBound), range.upperBound)
            let activity = Activity(id: UUID().uuidString, name: name, isUserAdded: false, createdAt: Date())
            let success = await todayViewModel.createPlan(
                activity: activity,
                scheduledTime: proposedDate,
                note: nil,
                location: trimmedLocation.isEmpty ? nil : trimmedLocation,
                locationName: selectedLocationName,
                locationLatitude: selectedLocationCoordinate?.latitude,
                locationLongitude: selectedLocationCoordinate?.longitude
            )
            if success {
                dismiss()
            } else {
                errorMessage = todayViewModel.errorMessage ?? "Couldn't post plan. Please try again."
            }
        case .groupInvite:
            guard let hostID = authService.currentUserID, !selectedInviteeIDs.isEmpty else { return }
            let activity = Activity(id: UUID().uuidString, name: name, isUserAdded: false, createdAt: Date())
            let plan = GroupPlan(
                hostID: hostID,
                inviteeIDs: Array(selectedInviteeIDs),
                activity: activity,
                location: trimmedLocation.isEmpty ? nil : trimmedLocation,
                locationName: selectedLocationName,
                locationLatitude: selectedLocationCoordinate?.latitude,
                locationLongitude: selectedLocationCoordinate?.longitude,
                date: proposedDate
            )
            do {
                try await GroupPlansService.shared.createGroupPlan(plan)
                dismiss()
            } catch {
                errorMessage = "Couldn't send invites. Please try again."
            }
        }
    }

    // MARK: - Time helpers

    /// .proposal and .groupInvite's default: next full hour, no day bound (a plan can be for
    /// any future day).
    private static func nextHourRoundedUp() -> Date {
        let now = Date()
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day, .hour], from: now)
        comps.hour = (comps.hour ?? 0) + 1
        comps.minute = 0
        return cal.date(from: comps) ?? now
    }

    /// .openPost's default: next full hour, clamped into the given day segment's range.
    private static func defaultPickerTime(for choice: DayChoice) -> Date {
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day, .hour], from: Date())
        comps.hour = (comps.hour ?? 0) + 1
        comps.minute = 0
        let nextHour = cal.date(from: comps) ?? Date().addingTimeInterval(3600)
        let range = choice == .today ? todayRange() : tomorrowRange()
        return min(max(nextHour, range.lowerBound), range.upperBound)
    }

    /// True from midnight until 15 minutes before the end of the current calendar day —
    /// i.e. there's still at least one valid 15-minute-out slot left in "Today".
    private static func todayIsAvailable() -> Bool {
        Date().addingTimeInterval(15 * 60) <= endOfToday()
    }

    private static func defaultDayChoice() -> DayChoice {
        todayIsAvailable() ? .today : .tomorrow
    }

    /// "Today" selectable range — 15-minute minimum lead, bounded to end of the current
    /// calendar day. Carried over exactly from the deleted CreateTodayPlanView.
    private static func todayRange() -> ClosedRange<Date> {
        let minimum = Date().addingTimeInterval(15 * 60)
        let maximum = endOfToday()
        return minimum <= maximum ? minimum...maximum : maximum...maximum
    }

    /// "Tomorrow" selectable range — from the next calendar day's midnight through exactly
    /// 24 hours from now (the rolling ceiling from the alpha-round-08 rule change).
    private static func tomorrowRange() -> ClosedRange<Date> {
        let minimum = startOfTomorrow()
        let maximum = Date().addingTimeInterval(24 * 60 * 60)
        return minimum <= maximum ? minimum...maximum : maximum...maximum
    }

    private static func startOfTomorrow() -> Date {
        let cal = Calendar.current
        let startOfToday = cal.startOfDay(for: Date())
        return cal.date(byAdding: .day, value: 1, to: startOfToday)!
    }

    private static func endOfToday() -> Date {
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        return cal.date(byAdding: .day, value: 1, to: start)!.addingTimeInterval(-1)
    }
}
