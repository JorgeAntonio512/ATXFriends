//
//  ProposePlanSheet.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 8/27/26.
//

import SwiftUI
import CoreLocation

/// The single plan-creation screen in the app. Four triggers present it:
///   - Matches → connected match row → calendar icon           (.proposal)
///   - Matches → row → MatchDetailView → "Propose a Plan"       (.proposal)
///   - Messages → thread → calendar icon                        (.proposal)
///   - Today → "+"                                              (.openPost)
///
/// .proposal writes a Plan (via PlansService, through MessagingViewModel.proposePlan) and posts
/// a planProposal message into the thread. .openPost writes a TodayPlan to todayPlans — no
/// recipient, claimable first-come-first-served. Everything about the view is shared by default;
/// a mode only changes something explicitly called out below.
struct ProposePlanSheet: View {
    enum Mode {
        case proposal(matchID: String, receiverID: String)
        case openPost
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
        _proposedDate = State(initialValue: Self.defaultTodayPickerTime())
    }

    private var isOpenPost: Bool {
        if case .openPost = mode { return true }
        return false
    }

    // ── form state ─────────────────────────────────────────────────────────
    @State private var activityName: String = ""
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

    private let authService = FirebaseAuthService.shared

    private var navTitle: String { isOpenPost ? "Post a Plan" : "Propose a Plan" }
    private var headerText: String {
        isOpenPost ? "Open to everyone on ATX Friends today" : "What do you want to do?"
    }

    var canSubmit: Bool {
        !activityName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !location.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !isSubmitting
    }

    /// Today's selectable time range — 15-minute minimum lead, bounded to end of today.
    /// Carried over exactly from the deleted CreateTodayPlanView.
    private var todayTimeRange: ClosedRange<Date> {
        let minimum = Date().addingTimeInterval(15 * 60)
        let maximum = Self.endOfToday()
        return minimum <= maximum ? minimum...maximum : maximum...maximum
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

                        // ── date + time ──────────────────────────────────
                        VStack(alignment: .leading, spacing: 8) {
                            Label("When", systemImage: "calendar")
                                .font(.system(size: 14, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)

                            if isOpenPost {
                                // Today-only — no month grid, just the same grey time pill
                                // that .graphical rendering shows beneath its calendar.
                                HStack {
                                    Text("Today")
                                        .font(.system(size: 16, weight: .medium, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                    Spacer()
                                    DatePicker(
                                        "",
                                        selection: $proposedDate,
                                        in: todayTimeRange,
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
                            } else {
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
                                    Image(systemName: isOpenPost ? "paperplane.fill" : "calendar.badge.plus")
                                    Text(isOpenPost ? "Post Plan" : "Send Proposal")
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

    /// Suggestion-chip source. .proposal draws from the global shared activity list.
    /// .openPost seeds from the user's own profile activities instead — the Today feed's
    /// filter chips group posts by exact activity-name match (TodayViewModel.availableActivities),
    /// so biasing toward the names the user already uses keeps posts groupable.
    private func loadActivities() async {
        switch mode {
        case .proposal:
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
        }
    }

    // MARK: - Time helpers

    /// .proposal's default: next full hour, no day bound (a proposal can be for any future day).
    private static func nextHourRoundedUp() -> Date {
        let now = Date()
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day, .hour], from: now)
        comps.hour = (comps.hour ?? 0) + 1
        comps.minute = 0
        return cal.date(from: comps) ?? now
    }

    /// .openPost's default: next full hour, clamped to the end of today. Carried over exactly
    /// from the deleted CreateTodayPlanView.
    private static func defaultTodayPickerTime() -> Date {
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day, .hour], from: Date())
        comps.hour = (comps.hour ?? 0) + 1
        comps.minute = 0
        let nextHour = cal.date(from: comps) ?? Date().addingTimeInterval(3600)
        return min(nextHour, endOfToday())
    }

    private static func endOfToday() -> Date {
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        return cal.date(byAdding: .day, value: 1, to: start)!.addingTimeInterval(-1)
    }
}
