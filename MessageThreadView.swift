//
//  MessageThreadView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import EventKit
import EventKitUI
import MapKit
import CoreLocation

/// The day word used by both the pinned-plan card's headline and the confirmed-plan empty
/// state, so the two stay in sync: "Tonight" (today, 5pm+), "Today" (today, earlier),
/// "Tomorrow", or a short weekday + date (e.g. "Sat, Oct 3").
fileprivate func planDayWord(for date: Date) -> String {
    let cal = Calendar.current
    if cal.isDateInToday(date) {
        return cal.component(.hour, from: date) >= 17 ? "Tonight" : "Today"
    } else if cal.isDateInTomorrow(date) {
        return "Tomorrow"
    } else {
        let df = DateFormatter()
        df.dateFormat = "EEE, MMM d"
        return df.string(from: date)
    }
}

/// Full-screen chat interface for a message thread
struct MessageThreadView: View {
    @Environment(\.dismiss) private var dismiss
    let thread: MessageThread
    @Bindable var viewModel: MessagingViewModel

    @State private var messageText: String = ""
    @FocusState private var isMessageFieldFocused: Bool
    @State private var showProposePlan = false
    /// Set when the sheet is opened from a shared-activity chip; nil for the calendar-plus
    /// button and the empty state's "Propose a plan" button, which open it blank.
    @State private var proposePlanPrefillActivity: String? = nil
    @State private var showReschedule = false
    @State private var showCancelAlert = false
    @State private var showAllPlans = false
    @State private var isPlanCardExpanded = true
    @State private var hasSetInitialPlanCardState = false

    @State private var calendarHandler = PlanCalendarActionHandler()

    @State private var showOtherUserProfile = false
    /// Set right before dismissing the profile sheet when its own "Propose a Plan" button was
    /// tapped, so the thread's ProposePlanSheet opens only after the profile sheet has fully
    /// dismissed — avoids presenting one sheet on top of another.
    @State private var pendingProposePlanAfterProfileDismiss = false
    @StateObject private var otherUserProfileViewModel = MatchesViewModel()

    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
    }

    /// The header avatar and empty-state photo only open a profile for regular match threads —
    /// event threads carry no Match, so there's nothing to build a MatchWithUser from.
    private var canViewOtherUserProfile: Bool {
        thread.match != nil
    }
    
    init(thread: MessageThread, viewModel: MessagingViewModel) {
        self.thread = thread
        self.viewModel = viewModel
        print("🟢 DEBUG: MessageThreadView init called for user: \(thread.otherUser.displayName)")
        print("🔍 DEBUG: Thread ID: \(thread.id)")
        print("🔍 DEBUG: Is Event Thread: \(thread.isEventThread)")
        if let match = thread.match {
            print("🔍 DEBUG: Match ID: \(match.id)")
        }
        if let event = thread.event {
            print("⚠️ WARNING: MessageThreadView being used for EVENT thread! Event ID: \(event.id)")
            print("⚠️ WARNING: This should use EventMessageThreadView instead!")
        }
    }
    
    /// The soonest confirmed upcoming plan. Re-checks the date client-side as
    /// a safety net against stale Firestore cache hits.
    private var activePinnedPlan: Plan? {
        viewModel.confirmedPlans.first(where: { ($0.confirmedDate ?? .distantPast) > Date() })
    }

    /// All confirmed upcoming plans beyond the pinned one — shown in the overflow sheet.
    private var overflowPlans: [Plan] {
        let upcoming = viewModel.confirmedPlans.filter { ($0.confirmedDate ?? .distantPast) > Date() }
        guard upcoming.count > 1 else { return [] }
        return Array(upcoming.dropFirst())
    }

    /// Messages to render in the thread. Hides planProposal messages whose plan is already
    /// confirmed — the pinned card is their home once confirmed, per viewModel.confirmedPlanIDs
    /// (the same live Firestore listener that drives the pinned card). Revert by changing this
    /// to `viewModel.messages`. Declined/cancelled proposals and pending ones are unaffected.
    private var visibleMessages: [Message] {
        viewModel.messages.filter { message in
            guard message.kind == .planProposal, let planID = message.planID else { return true }
            return !viewModel.confirmedPlanIDs.contains(planID)
        }
    }

    var body: some View {
        let _ = print("🟢 DEBUG: MessageThreadView body executing")
        NavigationStack {
            ZStack {
                Color.appBackground
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Pinned confirmed-plan card — hidden for event threads and when no plan exists
                    if !thread.isEventThread, let plan = activePinnedPlan {
                        PinnedPlanCard(
                            plan: plan,
                            overflowCount: overflowPlans.count,
                            otherUserDisplayName: thread.otherUser.displayName,
                            otherUserPhotoURL: thread.otherUser.photoURLs.first,
                            currentUserPhotoURL: viewModel.currentUserPhotoURL,
                            addedProviders: calendarHandler.addedProviders,
                            isExpanded: $isPlanCardExpanded,
                            onReschedule: { showReschedule = true },
                            onCancel: { showCancelAlert = true },
                            onShowAll: { showAllPlans = true },
                            onAddToCalendar: { provider in
                                calendarHandler.tap(
                                    provider: provider,
                                    planID: plan.id,
                                    fields: CalendarEventFields(
                                        plan: plan,
                                        otherUserDisplayName: thread.otherUser.displayName
                                    )
                                )
                            }
                        )
                    }

                    // Show-up prompt — appears once the plan time has passed and
                    // the current user hasn't submitted a report yet.
                    if !thread.isEventThread, let todayPlan = viewModel.pendingShowUpPlan {
                        ShowUpPromptCard(
                            otherUserName: thread.otherUser.displayName,
                            activityName: todayPlan.activity.name,
                            onThumbsUp: { await viewModel.submitShowUpReport(thumbsUp: true) },
                            onThumbsDown: { await viewModel.submitShowUpReport(thumbsUp: false) },
                            onDismiss: { viewModel.pendingShowUpPlan = nil }
                        )
                    }

                    // Messages scroll view
                    ScrollViewReader { proxy in
                        GeometryReader { scrollAreaProxy in
                        ScrollView {
                            VStack(spacing: 16) {
                                // Date header — omitted when filtering leaves nothing under it
                                if !visibleMessages.isEmpty {
                                    Text("Today")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 6)
                                        .background(Color.appCardBackground)
                                        .cornerRadius(12)
                                        .padding(.top, 20)
                                }
                                
                                // Messages
                                if visibleMessages.isEmpty {
                                    if !thread.isEventThread,
                                       let plan = activePinnedPlan,
                                       let confirmedDate = plan.confirmedDate {
                                        // Confirmed-plan empty state — a plan exists but no
                                        // chat messages yet.
                                        VStack(spacing: 16) {
                                            ZStack {
                                                Circle()
                                                    .fill(Color.appPrimary.opacity(0.2))
                                                    .frame(width: 80, height: 80)

                                                Image(systemName: "message.fill")
                                                    .font(.system(size: 36))
                                                    .foregroundColor(Color.appPrimary)
                                            }
                                            .padding(.top, 40)

                                            VStack(spacing: 8) {
                                                Text(emptyStateConfirmedPlanTitle(for: confirmedDate))
                                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                                    .foregroundColor(Color.appPrimaryText)

                                                Text("Say hi to \(thread.otherUser.displayName) before you head out.")
                                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                                    .foregroundColor(Color.appSecondaryText)
                                                    .multilineTextAlignment(.center)
                                                    .lineSpacing(4)
                                            }
                                        }
                                    } else {
                                        // Default empty state — no plan yet. Pushes toward
                                        // saying hi or proposing a plan right away.
                                        noMessagesYetView
                                            .frame(minHeight: scrollAreaProxy.size.height, alignment: .center)
                                    }
                                } else {
                                    // Message bubbles
                                    ForEach(visibleMessages, id: \.id) { message in
                                        messageBubble(for: message)
                                            .id(message.id)
                                            .transition(.opacity.combined(with: .move(edge: .top)))
                                    }
                                }
                                
                                Spacer()
                                    .frame(height: 20)
                            }
                        }
                        .scrollDismissesKeyboard(.interactively)
                        .safeAreaInset(edge: .bottom) {
                            // Add padding for custom tab bar (50pt) + safe area
                            GeometryReader { geometry in
                                Color.clear
                                    .frame(height: 50 + geometry.safeAreaInsets.bottom)
                            }
                            .frame(height: 50)
                        }
                        .onChange(of: viewModel.messages.count) { oldValue, newValue in
                            // Scroll to bottom when new message arrives
                            if let lastMessage = viewModel.messages.last {
                                withAnimation {
                                    proxy.scrollTo(lastMessage.id, anchor: .bottom)
                                }
                            }
                        }
                        }
                    }

                    // Input bar
                    HStack(alignment: .center, spacing: 8) {
                        // "+" propose-a-plan shortcut (only for regular match threads)
                        if !thread.isEventThread, thread.match != nil {
                            Button {
                                proposePlanPrefillActivity = nil
                                showProposePlan = true
                            } label: {
                                ZStack {
                                    Circle()
                                        .fill(Color.appPrimary.opacity(0.18))
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "calendar.badge.plus")
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(Color.appPrimary)
                                }
                            }
                        }

                        MessageInputBar(
                            text: $viewModel.draftMessage,
                            isFocused: $isMessageFieldFocused,
                            isSending: viewModel.isSendingMessage,
                            onSend: {
                                Task {
                                    if thread.isEventThread, let event = thread.event, let match = thread.match {
                                        print("📤 DEBUG: Sending EVENT message")
                                        await viewModel.sendEventMessage(
                                            matchID: match.id,
                                            eventID: event.id,
                                            receiverID: thread.otherUser.id
                                        )
                                    } else if let match = thread.match {
                                        print("📤 DEBUG: Sending REGULAR message for match: \(match.id)")
                                        await viewModel.sendMessage(
                                            matchID: match.id,
                                            receiverID: thread.otherUser.id
                                        )
                                    } else {
                                        print("❌ ERROR: Thread has neither event nor match!")
                                    }
                                }
                            }
                        )
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, 50) // custom tab bar height
                    .sheet(isPresented: $showProposePlan) {
                        if let match = thread.match {
                            ProposePlanSheet(
                                matchID: match.id,
                                receiverID: thread.otherUser.id,
                                viewModel: viewModel,
                                initialActivity: proposePlanPrefillActivity
                            )
                        }
                    }
                }
            }
            .navigationTitle(thread.otherUser.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(false)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color.appPrimary)
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        if canViewOtherUserProfile {
                            showOtherUserProfile = true
                        }
                    } label: {
                        AvatarRing(
                            photoURL: thread.otherUser.photoURLs.first,
                            displayName: thread.otherUser.displayName,
                            size: 36,
                            ringStyle: .soft
                        )
                        .accessibilityHidden(true)
                    }
                    .buttonStyle(.plain)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                    .accessibilityLabel("View \(thread.otherUser.displayName)'s profile")
                    .accessibilityAddTraits(.isButton)
                }
            }
        }
        .task {
            guard let match = thread.match else {
                print("❌ ERROR: MessageThreadView.task - No match found in thread!")
                print("❌ ERROR: Thread ID: \(thread.id)")
                print("❌ ERROR: Is Event Thread: \(thread.isEventThread)")
                return
            }
            print("🟢 DEBUG: .task modifier executing for match: \(match.id)")
            await viewModel.loadMessages(for: match.id)
            print("🟢 DEBUG: loadMessages completed")
            if !thread.isEventThread {
                viewModel.listenToConfirmedPlan(forMatch: match.id)
                await viewModel.loadPendingShowUpReport(otherUserID: thread.otherUser.id)
                await viewModel.loadOtherUserShowUpMeter(otherUserID: thread.otherUser.id)
                await viewModel.loadCurrentUserPhoto()
            }
        }
        .onAppear {
            print("🟢 DEBUG: MessageThreadView onAppear called")
            if let plan = activePinnedPlan {
                calendarHandler.loadAddedState(planID: plan.id)
            }
            // Lets willPresent suppress the foreground banner for a push about
            // this exact thread — the message/plan appears live instead.
            NotificationManager.shared.currentlyOpenMatchID = thread.id
        }
        .onDisappear {
            print("🟢 DEBUG: MessageThreadView onDisappear - stopping listener")
            viewModel.stopListening()
            if NotificationManager.shared.currentlyOpenMatchID == thread.id {
                NotificationManager.shared.currentlyOpenMatchID = nil
            }
        }
        .onChange(of: activePinnedPlan?.id) { _, newPlanID in
            if let newPlanID {
                calendarHandler.loadAddedState(planID: newPlanID)
                if !hasSetInitialPlanCardState {
                    isPlanCardExpanded = viewModel.messages.isEmpty
                    hasSetInitialPlanCardState = true
                }
            }
        }
        .onChange(of: isMessageFieldFocused) { _, isFocused in
            if isFocused {
                withAnimation(.snappy) { isPlanCardExpanded = false }
            }
        }
        .sheet(isPresented: $showReschedule) {
            if let plan = viewModel.pinnedPlan {
                ReschedulePlanSheet(plan: plan)
            }
        }
        .sheet(isPresented: $showAllPlans) {
            UpcomingPlansSheet(viewModel: viewModel, otherUserName: thread.otherUser.displayName)
        }
        .sheet(isPresented: $showOtherUserProfile, onDismiss: {
            if pendingProposePlanAfterProfileDismiss {
                pendingProposePlanAfterProfileDismiss = false
                proposePlanPrefillActivity = nil
                showProposePlan = true
            }
        }) {
            if let match = thread.match {
                MatchDetailView(
                    matchWithUser: MatchWithUser(match: match, otherUser: thread.otherUser),
                    viewModel: otherUserProfileViewModel,
                    onProposePlanRequested: {
                        pendingProposePlanAfterProfileDismiss = true
                        showOtherUserProfile = false
                    }
                )
                .task {
                    if otherUserProfileViewModel.currentUser == nil {
                        await otherUserProfileViewModel.loadCurrentUser()
                    }
                }
            }
        }
        .sheet(isPresented: Binding(
            get: { calendarHandler.eventToAdd != nil },
            set: { if !$0 { calendarHandler.eventToAdd = nil } }
        )) {
            if let eventToAdd = calendarHandler.eventToAdd, let plan = activePinnedPlan {
                EventEditView(
                    event: eventToAdd,
                    eventStore: calendarHandler.eventStore,
                    onComplete: { action in
                        calendarHandler.handleAppleEventEditCompletion(action, planID: plan.id)
                    }
                )
            }
        }
        .alert("Calendar Access Needed", isPresented: $calendarHandler.showCalendarPermissionAlert) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Enable calendar access in Settings to add this event.")
        }
        .alert("Couldn't Add to Google Calendar", isPresented: Binding(
            get: { calendarHandler.googleErrorMessage != nil },
            set: { if !$0 { calendarHandler.googleErrorMessage = nil } }
        )) {
            Button("OK") { calendarHandler.googleErrorMessage = nil }
        } message: {
            Text(calendarHandler.googleErrorMessage ?? "")
        }
        .alert("Couldn't Add to Outlook Calendar", isPresented: Binding(
            get: { calendarHandler.microsoftErrorMessage != nil },
            set: { if !$0 { calendarHandler.microsoftErrorMessage = nil } }
        )) {
            Button("OK") { calendarHandler.microsoftErrorMessage = nil }
        } message: {
            Text(calendarHandler.microsoftErrorMessage ?? "")
        }
        .alert("Cancel this plan?", isPresented: $showCancelAlert) {
            Button("Cancel Plan", role: .destructive) {
                Task {
                    if let plan = viewModel.pinnedPlan {
                        try? await PlansService.shared.cancelPlan(planID: plan.id)
                    }
                }
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("This will cancel your plan with \(thread.otherUser.displayName). You can always propose a new one.")
        }
        .alert("Couldn't Record Report", isPresented: Binding(
            get: { viewModel.showUpReportError != nil },
            set: { if !$0 { viewModel.showUpReportError = nil } }
        )) {
            Button("OK") { viewModel.showUpReportError = nil }
        } message: {
            Text(viewModel.showUpReportError ?? "")
        }
    }

    // MARK: - Helpers

    private func emptyStateConfirmedPlanTitle(for date: Date) -> String {
        let word = planDayWord(for: date)
        switch word {
        case "Tonight", "Today", "Tomorrow":
            return "You're on for \(word.lowercased())."
        default:
            return "You're on for \(word)."
        }
    }

    @ViewBuilder
    private func messageBubble(for message: Message) -> some View {
        if message.kind == .planProposal {
            PlanProposalCard(
                message: message,
                isFromCurrentUser: message.senderID == currentUserID
            )
        } else {
            MessageBubble(
                message: message,
                isFromCurrentUser: message.senderID == currentUserID
            )
        }
    }

    // MARK: - No-Messages-Yet Empty State

    private var emptyStateSubtitle: String {
        if thread.isEventThread, let event = thread.event {
            return "You're both going to \(event.name). Say hello!"
        }
        return "You matched. Say hi, or skip straight to a plan."
    }

    private var noMessagesYetView: some View {
        VStack(spacing: 20) {
            Button {
                if canViewOtherUserProfile {
                    showOtherUserProfile = true
                }
            } label: {
                AvatarRing(
                    photoURL: thread.otherUser.photoURLs.first,
                    displayName: thread.otherUser.displayName,
                    size: 112,
                    ringStyle: .soft
                )
                .accessibilityHidden(true)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("View \(thread.otherUser.displayName)'s profile")
            .accessibilityAddTraits(.isButton)

            VStack(spacing: 6) {
                Text(thread.otherUser.displayName)
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)

                Text(emptyStateSubtitle)
                    .font(.system(size: 15, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }

            if !thread.isEventThread, let match = thread.match, !match.overlappingActivityNames.isEmpty {
                VStack(spacing: 10) {
                    Text("You both like")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)

                    FlowLayout(spacing: 8) {
                        ForEach(match.overlappingActivityNames, id: \.self) { activityName in
                            activityChip(activityName)
                        }
                    }
                }
            }

            if !thread.isEventThread, thread.match != nil {
                Button {
                    proposePlanPrefillActivity = nil
                    showProposePlan = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 16, weight: .semibold))
                        Text("Propose a plan")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
                    .background(Color.appPrimary)
                    .cornerRadius(24)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Propose a plan")
            }
        }
        .padding(.horizontal, 40)
    }

    private func activityChip(_ activityName: String) -> some View {
        Button {
            proposePlanPrefillActivity = activityName
            showProposePlan = true
        } label: {
            Text(activityName)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(Color.appPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.appPrimary.opacity(0.15))
                .cornerRadius(16)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Propose a plan for \(activityName)")
    }
}

// MARK: - Pinned Plan Card

struct PinnedPlanCard: View {
    let plan: Plan
    let overflowCount: Int
    let otherUserDisplayName: String
    let otherUserPhotoURL: String?
    let currentUserPhotoURL: String?
    let addedProviders: Set<CalendarProvider>
    @Binding var isExpanded: Bool
    let onReschedule: () -> Void
    let onCancel: () -> Void
    let onShowAll: () -> Void
    let onAddToCalendar: (CalendarProvider) -> Void

    var body: some View {
        SwiftUI.Group {
            if isExpanded {
                expandedTicket
            } else {
                collapsedStrip
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    // MARK: - Expanded Ticket

    private var expandedTicket: some View {
        VStack(alignment: .leading, spacing: 14) {
            topRow

            TimelineView(.periodic(from: Date(), by: 60)) { context in
                VStack(alignment: .leading, spacing: 2) {
                    Text(headlineText(now: context.date))
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    Text(sublineText(now: context.date))
                        .font(.system(size: 15, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            }

            peopleRow

            locationSection

            buttonsRow
        }
        .padding(16)
        .background(cardBackground)
    }

    private var topRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 5) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
                Text("Plan confirmed")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
            }
            .foregroundColor(Color.appPositiveGreen)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.appPositiveGreen.opacity(0.14))
            .cornerRadius(20)

            if overflowCount > 0 {
                Button(action: onShowAll) {
                    Text("+\(overflowCount) more")
                        .font(.system(size: 12, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPositiveGreen)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.appPositiveGreen.opacity(0.12))
                        .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }

            Spacer()

            Menu {
                Button(action: onReschedule) {
                    Label("Reschedule", systemImage: "arrow.clockwise")
                }
                Button(role: .destructive, action: onCancel) {
                    Label("Cancel plan", systemImage: "xmark")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Color.appPrimaryText)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("More plan options")
        }
    }

    private var peopleRow: some View {
        HStack(spacing: 10) {
            ZStack(alignment: .leading) {
                personAvatar(url: otherUserPhotoURL, fallbackInitial: String(otherUserDisplayName.prefix(1)))
                    .offset(x: 20)
                personAvatar(url: currentUserPhotoURL, fallbackInitial: "Y")
            }
            .frame(width: 50, height: 30, alignment: .leading)

            Text("You & \(otherUserDisplayName)")
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(Color.appPrimaryText)
        }
    }

    @ViewBuilder
    private func personAvatar(url: String?, fallbackInitial: String) -> some View {
        ZStack {
            if let urlString = url, let imageURL = URL(string: urlString) {
                AsyncImage(url: imageURL) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        avatarFallback(fallbackInitial)
                    }
                }
            } else {
                avatarFallback(fallbackInitial)
            }
        }
        .frame(width: 30, height: 30)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.appCardBackground, lineWidth: 2))
    }

    private func avatarFallback(_ letter: String) -> some View {
        Circle()
            .fill(Color.appPrimary.opacity(0.7))
            .overlay {
                Text(letter.uppercased())
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
            }
    }

    @ViewBuilder
    private var locationSection: some View {
        if let lat = plan.locationLatitude, let lng = plan.locationLongitude {
            navigationMenu {
                PlanMapSnapshotView(
                    planID: plan.id,
                    coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
                    placeName: plan.locationName,
                    secondaryLine: secondaryPlaceLine
                )
            }
        } else if let location = plan.location, !location.isEmpty {
            navigationMenu {
                HStack(spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    Spacer()
                }
            }
        }
    }

    private var secondaryPlaceLine: String? {
        guard let location = plan.location, !location.isEmpty else { return nil }
        if let name = plan.locationName, location == name { return nil }
        return location
    }

    @ViewBuilder
    private func navigationMenu<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        Menu {
            ForEach(NavigationApp.availableApps) { app in
                Button {
                    if let url = app.directionsURL(for: plan) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label(app.displayName, systemImage: "location.fill")
                }
            }
        } label: {
            content()
        }
        .buttonStyle(.plain)
    }

    private var buttonsRow: some View {
        HStack(spacing: 10) {
            if hasLocation {
                directionsButton
                calendarIconButton
            } else {
                addToCalendarFullWidthButton
            }
        }
    }

    private var hasLocation: Bool {
        (plan.locationLatitude != nil && plan.locationLongitude != nil) || !(plan.location?.isEmpty ?? true)
    }

    private var directionsButton: some View {
        navigationMenu {
            HStack(spacing: 8) {
                Text("Get directions")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                Image(systemName: "arrow.right")
                    .font(.system(size: 15, weight: .semibold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Color.appPrimary)
            .cornerRadius(14)
        }
    }

    private var calendarIconButton: some View {
        Menu {
            calendarMenuItems
        } label: {
            Image(systemName: addedProviders.isEmpty ? "calendar.badge.plus" : "checkmark.circle.fill")
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(Color.appPrimary)
                .frame(width: 50, height: 50)
                .background(Color.appPrimary.opacity(0.12))
                .cornerRadius(14)
        }
        .accessibilityLabel("Add to calendar")
    }

    private var addToCalendarFullWidthButton: some View {
        Menu {
            calendarMenuItems
        } label: {
            HStack(spacing: 8) {
                Image(systemName: addedProviders.isEmpty ? "calendar.badge.plus" : "checkmark.circle.fill")
                Text(addedProviders.isEmpty ? "Add to Calendar" : "Added to Calendar")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Color.appPrimary)
            .cornerRadius(14)
        }
        .accessibilityLabel("Add to calendar")
    }

    @ViewBuilder
    private var calendarMenuItems: some View {
        ForEach(CalendarProvider.allCases) { provider in
            Button {
                onAddToCalendar(provider)
            } label: {
                Label(
                    addedProviders.contains(provider) ? "\(provider.displayName) — Added ✓" : provider.displayName,
                    systemImage: addedProviders.contains(provider) ? "checkmark.circle.fill" : "calendar"
                )
            }
        }
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 22)
            .fill(Color.appCardBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 22)
                    .stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.08), radius: 12, x: 0, y: 4)
    }

    // MARK: - Collapsed Strip

    private var collapsedStrip: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(Color.appPositiveGreen)
                .frame(width: 8, height: 8)

            Button {
                withAnimation(.snappy) { isExpanded = true }
            } label: {
                VStack(alignment: .leading, spacing: 1) {
                    Text(headlineText(now: Date()))
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    Text(collapsedSubtitle)
                        .font(.system(size: 12, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            if hasLocation {
                navigationMenu {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(Color.appPrimary)
                        .clipShape(Circle())
                }
                .accessibilityLabel("Get directions")
            }

            Button {
                withAnimation(.snappy) { isExpanded = true }
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color.appPrimaryText)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(cardBackground)
    }

    private var collapsedSubtitle: String {
        if let name = plan.locationName, !name.isEmpty {
            return "\(plan.activity.name) · \(name)"
        } else if let location = plan.location, !location.isEmpty {
            return "\(plan.activity.name) · \(location)"
        }
        return plan.activity.name
    }

    // MARK: - Headline / Countdown

    private func headlineText(now: Date) -> String {
        guard let date = plan.confirmedDate else { return plan.activity.name }
        return "\(planDayWord(for: date)) · \(timeString(date))"
    }

    private func sublineText(now: Date) -> String {
        guard let date = plan.confirmedDate else { return plan.activity.name }
        if let suffix = countdownSuffix(for: date, now: now) {
            return "\(plan.activity.name) · \(suffix)"
        }
        return plan.activity.name
    }

    private func timeString(_ date: Date) -> String {
        let df = DateFormatter()
        df.dateFormat = "h:mm a"
        return df.string(from: date)
    }

    private func countdownSuffix(for date: Date, now: Date) -> String? {
        let diff = date.timeIntervalSince(now)
        if diff >= 0 && diff <= 3 * 3600 {
            let minutes = Int((diff / 60).rounded())
            if minutes < 60 {
                return "starts in \(max(minutes, 1)) min"
            }
            let hours = Int((diff / 3600).rounded())
            return "starts in \(hours) hr"
        } else if diff < 0 && diff >= -2 * 3600 {
            return "happening now"
        }
        return nil
    }
}

/// In-memory cache of rendered map snapshots, keyed by plan ID, so the same plan's map
/// isn't re-rendered by MKMapSnapshotter on every card redraw.
@MainActor
final class PlanMapSnapshotCache {
    static let shared = PlanMapSnapshotCache()
    private var images: [String: UIImage] = [:]

    func image(for planID: String) -> UIImage? { images[planID] }
    func store(_ image: UIImage, for planID: String) { images[planID] = image }
}

/// A small static map with a pin at the plan's location, used by the expanded ticket.
private struct PlanMapSnapshotView: View {
    let planID: String
    let coordinate: CLLocationCoordinate2D
    let placeName: String?
    let secondaryLine: String?

    @Environment(\.displayScale) private var displayScale
    @State private var image: UIImage?

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.appPrimary.opacity(0.12))
                .overlay {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 16))

            if let placeName, !placeName.isEmpty {
                VStack(alignment: .leading, spacing: 1) {
                    Text(placeName)
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    if let secondaryLine, !secondaryLine.isEmpty {
                        Text(secondaryLine)
                            .font(.system(size: 11, design: .rounded))
                            .foregroundColor(Color.appNavy.opacity(0.7))
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.92))
                .cornerRadius(10)
                .padding(8)
            }
        }
        .frame(height: 118)
        .task(id: planID) {
            await loadSnapshot()
        }
    }

    private func loadSnapshot() async {
        if let cached = PlanMapSnapshotCache.shared.image(for: planID) {
            image = cached
            return
        }
        let options = MKMapSnapshotter.Options()
        options.region = MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: 600,
            longitudinalMeters: 600
        )
        options.size = CGSize(width: 360, height: 236)
        options.scale = displayScale

        guard let snapshot = try? await MKMapSnapshotter(options: options).start() else { return }
        let rendered = Self.renderPin(on: snapshot, at: coordinate)
        PlanMapSnapshotCache.shared.store(rendered, for: planID)
        image = rendered
    }

    private static func renderPin(on snapshot: MKMapSnapshotter.Snapshot, at coordinate: CLLocationCoordinate2D) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: snapshot.image.size)
        return renderer.image { _ in
            snapshot.image.draw(at: .zero)
            let point = snapshot.point(for: coordinate)
            let pinImage = UIImage(systemName: "mappin.circle.fill")?
                .withTintColor(.systemOrange, renderingMode: .alwaysOriginal)
            let pinSize = CGSize(width: 28, height: 28)
            let pinOrigin = CGPoint(x: point.x - pinSize.width / 2, y: point.y - pinSize.height)
            pinImage?.draw(in: CGRect(origin: pinOrigin, size: pinSize))
        }
    }
}

// MARK: - Show-Up Prompt Card

/// Appears at the top of a thread once a TodayPlan's scheduled time has passed
/// and the current user hasn't submitted a show-up report yet.
struct ShowUpPromptCard: View {
    let otherUserName: String
    let activityName: String
    let onThumbsUp: () async -> Bool
    let onThumbsDown: () async -> Bool
    let onDismiss: () -> Void

    @State private var isActing = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Color.appPromptGold)
                    Text("How did it go?")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPromptGold)
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color.appSecondaryText)
                }
                .buttonStyle(.plain)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Your \(activityName) hangout has passed.")
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                Text("Did \(otherUserName) show up?")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
            }

            HStack(spacing: 10) {
                Button {
                    guard !isActing else { return }
                    isActing = true
                    Task {
                        let success = await onThumbsDown()
                        if !success { isActing = false }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "hand.thumbsdown.fill")
                            .font(.system(size: 14))
                        Text("Didn't show")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(Color.appDeclinedRed)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color.appDeclinedRed.opacity(0.10))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
                .disabled(isActing)

                Button {
                    guard !isActing else { return }
                    isActing = true
                    Task {
                        let success = await onThumbsUp()
                        if !success { isActing = false }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "hand.thumbsup.fill")
                            .font(.system(size: 14))
                        Text("They showed up!")
                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
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
                }
                .buttonStyle(.plain)
                .disabled(isActing)
            }
            .padding(.top, 2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.appPromptCreamBg)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color.appPromptGoldAccent)
                .frame(height: 3)
        }
    }
}

// MARK: - Reschedule Plan Sheet

struct ReschedulePlanSheet: View {
    @Environment(\.dismiss) private var dismiss
    let plan: Plan

    @State private var newDate: Date
    @State private var isSubmitting = false

    init(plan: Plan) {
        self.plan = plan
        let base = plan.confirmedDate ?? Date()
        self._newDate = State(initialValue: base > Date() ? base : Date().addingTimeInterval(3600))
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground
                    .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Suggest a new time")
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appPrimaryText)
                            Text("Your friend will need to re-confirm.")
                                .font(.system(size: 14, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                        }
                        .padding(.top, 8)

                        DatePicker(
                            "",
                            selection: $newDate,
                            in: Date()...,
                            displayedComponents: [.date, .hourAndMinute]
                        )
                        .datePickerStyle(.graphical)
                        .tint(Color.appPrimary)
                        .background(Color.appCardBackground)
                        .cornerRadius(12)

                        Button {
                            Task { await submit() }
                        } label: {
                            ZStack {
                                if isSubmitting {
                                    ProgressView().tint(.white)
                                } else {
                                    HStack(spacing: 8) {
                                        Image(systemName: "arrow.clockwise")
                                        Text("Suggest New Time")
                                    }
                                }
                            }
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
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
                            .cornerRadius(16)
                        }
                        .disabled(isSubmitting)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Reschedule")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(Color.appPrimary)
                }
            }
        }
    }

    private func submit() async {
        isSubmitting = true
        defer { isSubmitting = false }
        try? await PlansService.shared.counterPropose(planID: plan.id, newDates: [newDate])
        dismiss()
    }
}

// MARK: - Upcoming Plans Sheet

/// Sheet listing all confirmed upcoming plans beyond the pinned (soonest) one.
/// Observes viewModel.confirmedPlans live so cancellations take effect immediately.
struct UpcomingPlansSheet: View {
    @Environment(\.dismiss) private var dismiss
    let viewModel: MessagingViewModel
    let otherUserName: String

    @State private var showCancelAlertFor: Plan? = nil

    private var plans: [Plan] {
        let upcoming = viewModel.confirmedPlans.filter { ($0.confirmedDate ?? .distantPast) > Date() }
        guard upcoming.count > 1 else { return [] }
        return Array(upcoming.dropFirst())
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(plans) { plan in
                            UpcomingPlanRow(plan: plan) {
                                showCancelAlertFor = plan
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .padding(.bottom, 40)
                }
            }
            .navigationTitle("More upcoming plans")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }
            .alert("Cancel this plan?", isPresented: Binding(
                get: { showCancelAlertFor != nil },
                set: { if !$0 { showCancelAlertFor = nil } }
            )) {
                Button("Cancel Plan", role: .destructive) {
                    if let plan = showCancelAlertFor {
                        Task { try? await PlansService.shared.cancelPlan(planID: plan.id) }
                        showCancelAlertFor = nil
                    }
                }
                Button("Keep", role: .cancel) { showCancelAlertFor = nil }
            } message: {
                if let plan = showCancelAlertFor {
                    Text("Cancel your \(plan.activity.name) plan with \(otherUserName)?")
                }
            }
            .onChange(of: plans.count) { _, count in
                if count == 0 { dismiss() }
            }
        }
    }
}

// MARK: - Upcoming Plan Row

struct UpcomingPlanRow: View {
    let plan: Plan
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(plan.activity.name)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
                Spacer()
                Button(action: onCancel) {
                    HStack(spacing: 3) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .semibold))
                        Text("Cancel")
                            .font(.system(size: 12, weight: .medium, design: .rounded))
                    }
                    .foregroundColor(Color.appDeclinedRed)
                }
                .buttonStyle(.plain)
            }

            if let date = plan.confirmedDate {
                HStack(spacing: 4) {
                    Image(systemName: "clock.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Color.appSecondaryText)
                    Text(formattedPlanDate(date))
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            }

            if let location = plan.location, !location.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            }
        }
        .padding(14)
        .background(Color.appPositiveCardBg)
        .cornerRadius(12)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color.appPositiveGreen)
                .frame(height: 3)
        }
    }

    private func formattedPlanDate(_ date: Date) -> String {
        let cal = Calendar.current
        let tf = DateFormatter()
        tf.dateFormat = "h:mma"
        tf.amSymbol = "am"
        tf.pmSymbol = "pm"
        let timeStr = tf.string(from: date).replacingOccurrences(of: ":00", with: "")
        if cal.isDateInToday(date) { return "Today at \(timeStr)" }
        if cal.isDateInTomorrow(date) { return "Tomorrow at \(timeStr)" }
        let df = DateFormatter()
        df.dateFormat = "EEE, MMM d"
        return "\(df.string(from: date)) at \(timeStr)"
    }
}

#Preview {
    let user = User(
        id: "user2",
        displayName: "Jordan",
        photoURLs: ["url1", "url2", "url3"],
        activities: [
            ActivityModel(name: "Hiking", isUserAdded: false),
            ActivityModel(name: "Coffee", isUserAdded: false),
            ActivityModel(name: "Reading", isUserAdded: false)
        ],
        daySlotCombos: [
            DaySlotComboModel(dayOfWeek: .saturday, timeSlot: .wakeUp),
            DaySlotComboModel(dayOfWeek: .sunday, timeSlot: .afternoon),
            DaySlotComboModel(dayOfWeek: .friday, timeSlot: .evening)
        ],
        latitude: 30.2700,
        longitude: -97.7400,
        isProfileComplete: true
    )
    
    let match = Match(
        user1ID: "user1",
        user2ID: "user2",
        user1Decision: true,
        user2Decision: true,
        isMutualMatch: true,
        overlappingActivityNames: ["Hiking", "Coffee"],
        overlappingDaySlots: ["Saturday Wake Up"]
    )
    
    let thread = MessageThread(
        id: match.id,
        match: match,
        otherUser: user,
        lastMessage: nil,
        unreadCount: 0
    )
    
    MessageThreadView(thread: thread, viewModel: MessagingViewModel())
}
