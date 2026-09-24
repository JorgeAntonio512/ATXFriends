//
//  TodayView.swift
//  Avenue3
//

import SwiftUI

struct TodayView: View {
    @State private var viewModel = TodayViewModel()
    @State private var showCreatePlan = false
    @State private var selectedGhostSlot: OpenSlot?

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

                VStack(spacing: 0) {
                    // Activity filter chips — only visible when there's something to filter
                    if !viewModel.availableActivities.isEmpty {
                        activityFilterBar
                            .padding(.horizontal, 20)
                            .padding(.top, 12)
                            .padding(.bottom, 4)
                    }

                    if viewModel.isLoading {
                        Spacer()
                        VStack(spacing: 16) {
                            ProgressView()
                                .tint(Color.appPrimary)
                                .scaleEffect(1.2)
                            Text("Loading plans…")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 14) {
                                if viewModel.filteredPlans.isEmpty {
                                    emptyLine
                                } else {
                                    ForEach(viewModel.filteredPlans) { plan in
                                        TodayPlanCard(
                                            plan: plan,
                                            posterInfo: viewModel.posterInfo[plan.creatorID],
                                            isOwnPlan: plan.creatorID == viewModel.currentUserID,
                                            onClaim: {
                                                await viewModel.claimPlan(plan)
                                            }
                                        )
                                    }
                                }

                                if !viewModel.rankedOpenSlots.isEmpty {
                                    openSlotsSection
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 16)
                        }
                        .scrollIndicators(.hidden)
                        .refreshable { await viewModel.loadOpenPlans() }
                    }
                }
            }
            .navigationTitle("Today")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showCreatePlan = true
                    } label: {
                        Image(systemName: "plus")
                            .foregroundColor(Color.appPrimary)
                    }
                }
            }
            .sheet(isPresented: $showCreatePlan) {
                ProposePlanSheet(todayViewModel: viewModel)
            }
            .sheet(item: $selectedGhostSlot) { slot in
                ProposePlanSheet(todayViewModel: viewModel, prefill: openPostPrefill(for: slot))
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            // Poster banner: slides down from the nav bar when someone claims their plan
            .overlay(alignment: .top) {
                if let message = viewModel.claimedPlanBanner {
                    claimedBanner(message: message)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .padding(.top, 8)
                }
            }
            // Post toast: brief confirmation after posting from a ghost card or from scratch
            .overlay(alignment: .bottom) {
                if let toast = viewModel.postToast {
                    postToastView(message: toast)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .padding(.bottom, 24)
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.75), value: viewModel.claimedPlanBanner)
            .animation(.spring(response: 0.4, dampingFraction: 0.75), value: viewModel.postToast)
        }
        .task {
            await viewModel.loadOpenPlans()
            viewModel.setupListener()
        }
        .onDisappear {
            viewModel.removeListener()
        }
    }

    /// Builds the .openPost prefill for a tapped ghost card, choosing Today/Tomorrow based
    /// on which calendar day the slot's start time falls on.
    private func openPostPrefill(for slot: OpenSlot) -> ProposePlanSheet.OpenPostPrefill {
        let dayChoice: ProposePlanSheet.DayChoice = Calendar.current.isDateInToday(slot.start) ? .today : .tomorrow
        return ProposePlanSheet.OpenPostPrefill(activityName: slot.activityName, dayChoice: dayChoice, date: slot.start)
    }

    // MARK: - Claimed Banner

    private func claimedBanner(message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "person.fill.checkmark")
                .font(.system(size: 15, weight: .semibold))
            Text(message)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
        .background(
            LinearGradient(
                colors: [
                    Color.appPrimary,
                    Color.appPrimary
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(14)
        .shadow(color: Color.appPrimary.opacity(0.30), radius: 8, x: 0, y: 4)
        .padding(.horizontal, 20)
        .onAppear {
            Task {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                withAnimation { viewModel.claimedPlanBanner = nil }
            }
        }
    }

    // MARK: - Post Toast

    private func postToastView(message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 15, weight: .semibold))
            Text(message)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(Color.appNavy)
        .cornerRadius(20)
        .shadow(color: .black.opacity(0.15), radius: 8, x: 0, y: 4)
        .onAppear {
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                withAnimation { viewModel.postToast = nil }
            }
        }
    }

    // MARK: - Activity Filter Bar

    private var activityFilterBar: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 8) {
                filterChip(label: "All", isSelected: viewModel.activityFilter == nil) {
                    viewModel.activityFilter = nil
                }
                ForEach(viewModel.availableActivities, id: \.self) { name in
                    filterChip(label: name, isSelected: viewModel.activityFilter == name) {
                        viewModel.activityFilter = (viewModel.activityFilter == name) ? nil : name
                    }
                }
            }
            .padding(.vertical, 4)
        }
        .scrollIndicators(.hidden)
    }

    private func filterChip(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(isSelected ? .white : Color.appPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    isSelected
                        ? Color.appPrimary
                        : Color.white.opacity(0.65)
                )
                .cornerRadius(20)
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.25), value: isSelected)
    }

    // MARK: - Empty Line

    /// A single small muted line — the ghost cards below are the call to action now, so this
    /// no longer needs a giant icon or a "tap + to post" subtitle.
    private var emptyLine: some View {
        Text(
            viewModel.activityFilter.map { "No \($0) plans today" }
                ?? "Nobody's posted yet. Be the first one."
        )
        .font(.system(size: 14, weight: .medium, design: .rounded))
        .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
        .padding(.bottom, 4)
    }

    // MARK: - Open Slots (Ghost Cards)

    private var openSlotsSectionHeader: String {
        if !viewModel.filteredPlans.isEmpty {
            return "Or post your own"
        }
        return viewModel.allOpenSlotsAreFallback ? "Free in the next day?" : "Your open slots"
    }

    private var openSlotsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(openSlotsSectionHeader)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .foregroundColor(Color.appNavy)
                .padding(.top, 4)

            ForEach(viewModel.rankedOpenSlots) { ranked in
                GhostSlotCardView(
                    titleLine: Self.titleLine(for: ranked.slot.start),
                    activityName: ranked.slot.activityName,
                    buttonIcon: "plus",
                    buttonLabel: "Post it",
                    accessibilityLabel: "Post a plan: \(ranked.slot.activityName), \(Self.accessibleTimeDescription(for: ranked.slot.start)).",
                    onTap: { selectedGhostSlot = ranked.slot }
                )
            }

            if let next = viewModel.nextUsualSlot {
                Button {
                    NotificationCenter.default.post(name: .navigateToUpcoming, object: nil)
                } label: {
                    HStack(spacing: 4) {
                        Text("Your next usual slot: \(next.dayOfWeek.rawValue) \(next.timeSlot.rawValue.lowercased())")
                        Image(systemName: "arrow.right")
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                }
                .buttonStyle(.plain)
                .padding(.top, 2)
            }

            Button {
                showCreatePlan = true
            } label: {
                Text("Or start from scratch")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.top, 4)
        }
    }

    /// "Tonight at 7" / "Today at 8am" / "Tomorrow at noon"
    static func titleLine(for date: Date) -> String {
        let cal = Calendar.current
        let hour = cal.component(.hour, from: date)
        let minute = cal.component(.minute, from: date)
        let isToday = cal.isDateInToday(date)
        let isEveningHour = hour >= 17 || hour < 5

        if isToday && isEveningHour {
            return "Tonight at \(compactHourNoSuffix(hour: hour, minute: minute))"
        }
        let dayWord = isToday ? "Today" : "Tomorrow"
        if hour == 12 && minute == 0 { return "\(dayWord) at noon" }
        if hour == 0 && minute == 0 { return "\(dayWord) at midnight" }
        return "\(dayWord) at \(compactTime(hour: hour, minute: minute))"
    }

    /// "tonight at 7 PM" / "today at 8 AM" / "tomorrow at 8 AM" — for VoiceOver.
    static func accessibleTimeDescription(for date: Date) -> String {
        let cal = Calendar.current
        let hour = cal.component(.hour, from: date)
        let minute = cal.component(.minute, from: date)
        let isToday = cal.isDateInToday(date)
        let isEveningHour = hour >= 17 || hour < 5
        let dayWord = isToday ? (isEveningHour ? "tonight" : "today") : "tomorrow"

        let formatter = DateFormatter()
        formatter.dateFormat = minute == 0 ? "h a" : "h:mm a"
        return "\(dayWord) at \(formatter.string(from: date))"
    }

    private static func compactHourNoSuffix(hour: Int, minute: Int) -> String {
        var displayHour = hour % 12
        if displayHour == 0 { displayHour = 12 }
        if minute == 0 { return "\(displayHour)" }
        return String(format: "%d:%02d", displayHour, minute)
    }

    private static func compactTime(hour: Int, minute: Int) -> String {
        var displayHour = hour % 12
        if displayHour == 0 { displayHour = 12 }
        let suffix = hour >= 12 ? "pm" : "am"
        if minute == 0 { return "\(displayHour)\(suffix)" }
        return String(format: "%d:%02d%@", displayHour, minute, suffix)
    }
}

// MARK: - Today Plan Card

struct TodayPlanCard: View {
    let plan: TodayPlan
    let posterInfo: TodayViewModel.PosterInfo?
    let isOwnPlan: Bool
    /// Async closure; returns true if the claim succeeded, false if it failed.
    let onClaim: () async -> Bool

    @State private var isClaiming = false

    /// "Today 7:00 PM" or "Tomorrow 9:00 AM" — the calendar-day comparison is re-evaluated on
    /// every render, so a plan posted for "Tomorrow" relabels itself once midnight passes.
    static func timeBadgeLabel(for date: Date) -> String {
        let time = date.formatted(date: .omitted, time: .shortened)
        let cal = Calendar.current
        if cal.isDateInToday(date) {
            return "Today \(time)"
        } else if cal.isDateInTomorrow(date) {
            return "Tomorrow \(time)"
        } else {
            return time
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {

            // Row 1: activity name + time badge
            HStack(alignment: .top) {
                Text(plan.activity.name)
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                Spacer()
                Text(Self.timeBadgeLabel(for: plan.scheduledTime))
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.appPrimary)
                    .cornerRadius(10)
            }

            // Row 2: poster name + show-up meter
            HStack(spacing: 5) {
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 13))
                    .foregroundColor(Color.appPrimary)
                Text(posterInfo?.displayName ?? "…")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                Text("·")
                    .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 11))
                    .foregroundColor(Color.appPrimary)
                Text(posterInfo?.showUpMeter ?? "New")
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
            }

            // Row 3: location
            if let location = plan.location, !location.isEmpty {
                HStack(spacing: 5) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 12))
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                        .lineLimit(1)
                }
            }

            // Row 4: optional note
            if let note = plan.note, !note.isEmpty {
                Text(note)
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                    .italic()
                    .lineLimit(2)
            }

            // Row 4: action
            HStack {
                Spacer()
                if isOwnPlan {
                    Text("Your post")
                        .font(.system(size: 13, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.65, green: 0.65, blue: 0.65))
                } else {
                    Button {
                        guard !isClaiming else { return }
                        isClaiming = true
                        Task {
                            let success = await onClaim()
                            // On failure reset so the user can try another plan or see the error.
                            // On success the card disappears from the feed via the listener.
                            if !success {
                                isClaiming = false
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if isClaiming {
                                ProgressView()
                                    .tint(.white)
                                    .scaleEffect(0.75)
                                    .frame(width: 14, height: 14)
                            } else {
                                Image(systemName: "hand.thumbsup.fill")
                                    .font(.system(size: 13))
                            }
                            Text(isClaiming ? "Joining…" : "I'm in")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 9)
                        .background(
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(12)
                        .shadow(
                            color: Color.appPrimary.opacity(0.30),
                            radius: 6, x: 0, y: 3
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isClaiming)
                }
            }
        }
        .padding(16)
        .background(Color.white.opacity(0.85))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
    }
}
