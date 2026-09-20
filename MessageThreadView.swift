//
//  MessageThreadView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import EventKit
import EventKitUI

/// Full-screen chat interface for a message thread
struct MessageThreadView: View {
    @Environment(\.dismiss) private var dismiss
    let thread: MessageThread
    @Bindable var viewModel: MessagingViewModel

    @State private var messageText: String = ""
    @FocusState private var isMessageFieldFocused: Bool
    @State private var showProposePlan = false
    @State private var showReschedule = false
    @State private var showCancelAlert = false
    @State private var showAllPlans = false

    @State private var calendarHandler = PlanCalendarActionHandler()
    
    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
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

    var body: some View {
        let _ = print("🟢 DEBUG: MessageThreadView body executing")
        NavigationStack {
            ZStack {
                // Warm gradient background
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
                    // Pinned confirmed-plan card — hidden for event threads and when no plan exists
                    if !thread.isEventThread, let plan = activePinnedPlan {
                        PinnedPlanCard(
                            plan: plan,
                            overflowCount: overflowPlans.count,
                            otherUserShowUpMeter: viewModel.otherUserShowUpMeter,
                            addedProviders: calendarHandler.addedProviders,
                            onReschedule: { showReschedule = true },
                            onCancel: { showCancelAlert = true },
                            onShowAll: { showAllPlans = true },
                            onAddToCalendar: { provider in
                                calendarHandler.tap(
                                    provider: provider,
                                    plan: plan,
                                    otherUserDisplayName: thread.otherUser.displayName
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
                        ScrollView {
                            VStack(spacing: 16) {
                                // Date header
                                Text("Today")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 6)
                                    .background(Color.white.opacity(0.6))
                                    .cornerRadius(12)
                                    .padding(.top, 20)
                                
                                // Messages
                                if viewModel.messages.isEmpty {
                                    // Empty conversation state
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
                                            Text("Start the conversation!")
                                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                                .foregroundColor(Color.appNavy)
                                            
                                            if thread.isEventThread, let event = thread.event {
                                                Text("You're both going to \(event.name).\nSay hello!")
                                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                                    .multilineTextAlignment(.center)
                                                    .lineSpacing(4)
                                            } else {
                                                Text("You matched with \(thread.otherUser.displayName).\nSay hello!")
                                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                                    .multilineTextAlignment(.center)
                                                    .lineSpacing(4)
                                            }
                                        }
                                        
                                        // Shared interests hint
                                        if let match = thread.match, !match.overlappingActivityNames.isEmpty {
                                            VStack(alignment: .leading, spacing: 8) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "heart.fill")
                                                        .font(.system(size: 14))
                                                        .foregroundColor(Color.appPrimary)
                                                    
                                                    Text("You both like:")
                                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                                        .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                                                }
                                                
                                                Text(match.overlappingActivityNames.joined(separator: ", "))
                                                    .font(.system(size: 14, weight: .regular, design: .rounded))
                                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                            }
                                            .padding()
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(Color.appPrimary.opacity(0.1))
                                            .cornerRadius(12)
                                            .padding(.horizontal, 40)
                                        }
                                    }
                                } else {
                                    // Message bubbles
                                    ForEach(viewModel.messages, id: \.id) { message in
                                        messageBubble(for: message)
                                            .id(message.id)
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
                    
                    // Input bar
                    VStack(spacing: 0) {
                        HStack(alignment: .bottom, spacing: 8) {
                            // "+" propose-a-plan shortcut (only for regular match threads)
                            if !thread.isEventThread, thread.match != nil {
                                Button {
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
                                .padding(.leading, 16)
                                .padding(.bottom, 12)
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
                        .padding(.bottom, 50) // custom tab bar height
                    }
                    .sheet(isPresented: $showProposePlan) {
                        if let match = thread.match {
                            ProposePlanSheet(
                                matchID: match.id,
                                receiverID: thread.otherUser.id,
                                viewModel: viewModel
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
                    // Profile button (future enhancement)
                    Button {
                        // Show user profile
                    } label: {
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary.opacity(0.3),
                                            Color.appPrimary.opacity(0.3)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 32, height: 32)
                            
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 24))
                                .foregroundColor(Color.appPrimary.opacity(0.7))
                        }
                    }
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
            }
        }
        .onAppear {
            print("🟢 DEBUG: MessageThreadView onAppear called")
            if let plan = activePinnedPlan {
                calendarHandler.loadAddedState(planID: plan.id)
            }
        }
        .onDisappear {
            print("🟢 DEBUG: MessageThreadView onDisappear - stopping listener")
            viewModel.stopListening()
        }
        .onChange(of: activePinnedPlan?.id) { _, newPlanID in
            if let newPlanID {
                calendarHandler.loadAddedState(planID: newPlanID)
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
}

// MARK: - Pinned Plan Card

struct PinnedPlanCard: View {
    let plan: Plan
    let overflowCount: Int
    let otherUserShowUpMeter: String
    let addedProviders: Set<CalendarProvider>
    let onReschedule: () -> Void
    let onCancel: () -> Void
    let onShowAll: () -> Void
    let onAddToCalendar: (CalendarProvider) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "calendar.badge.checkmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Color(red: 0.30, green: 0.58, blue: 0.35))
                Text("Plan confirmed")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.30, green: 0.58, blue: 0.35))
                Spacer()
                if overflowCount > 0 {
                    Button(action: onShowAll) {
                        Text("+\(overflowCount) more")
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.30, green: 0.58, blue: 0.35))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(red: 0.30, green: 0.58, blue: 0.35).opacity(0.12))
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(plan.activity.name)
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.25, green: 0.25, blue: 0.25))

                    if let date = plan.confirmedDate {
                        HStack(spacing: 4) {
                            Image(systemName: "clock.fill")
                                .font(.system(size: 11))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            Text(formattedDate(date))
                                .font(.system(size: 13, design: .rounded))
                                .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                        }
                    }

                    if let location = plan.location, !location.isEmpty {
                        HStack(spacing: 4) {
                            Image(systemName: "mappin.circle.fill")
                                .font(.system(size: 11))
                                .foregroundColor(Color.appPrimary)
                            Text(location)
                                .font(.system(size: 13, design: .rounded))
                                .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                        }
                    }

                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 11))
                            .foregroundColor(Color.appPrimary)
                        Text(otherUserShowUpMeter)
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundColor(Color.appPrimary)
                    }
                }
                Spacer()
            }

            Divider()
                .background(Color.appPrimary.opacity(0.20))

            HStack(spacing: 8) {
                if let location = plan.location, !location.isEmpty {
                    navigationActionChip(tint: Color(red: 0.27, green: 0.49, blue: 0.78))
                }

                calendarActionChip(
                    addedProviders: addedProviders,
                    tint: Color.appPrimary,
                    onSelect: onAddToCalendar
                )

                actionChip(
                    label: "Reschedule",
                    icon: "arrow.clockwise",
                    tint: Color.appPrimary
                ) { onReschedule() }

                Spacer()

                Button(action: onCancel) {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark")
                            .font(.system(size: 11, weight: .semibold))
                        Text("Cancel plan")
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                    }
                    .foregroundColor(Color(red: 0.72, green: 0.33, blue: 0.28))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(red: 0.93, green: 0.97, blue: 0.93))
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color(red: 0.30, green: 0.58, blue: 0.35))
                .frame(height: 3)
        }
    }

    private func actionChip(
        label: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .semibold))
                Text(label)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
            }
            .foregroundColor(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }

    private func calendarActionChip(
        addedProviders: Set<CalendarProvider>,
        tint: Color,
        onSelect: @escaping (CalendarProvider) -> Void
    ) -> some View {
        Menu {
            ForEach(CalendarProvider.allCases) { provider in
                Button {
                    onSelect(provider)
                } label: {
                    Label(
                        addedProviders.contains(provider) ? "\(provider.displayName) — Added ✓" : provider.displayName,
                        systemImage: addedProviders.contains(provider) ? "checkmark.circle.fill" : "calendar"
                    )
                }
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: addedProviders.isEmpty ? "calendar.badge.plus" : "checkmark.circle.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text(addedProviders.isEmpty ? "Add to Calendar" : "Added ✓")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
            }
            .foregroundColor(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12))
            .cornerRadius(8)
        }
    }

    private func navigationActionChip(tint: Color) -> some View {
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
            HStack(spacing: 5) {
                Image(systemName: "map.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text("Directions")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
            }
            .foregroundColor(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12))
            .cornerRadius(8)
        }
    }

    private func formattedDate(_ date: Date) -> String {
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
                        .foregroundColor(Color(red: 0.60, green: 0.45, blue: 0.20))
                    Text("How did it go?")
                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.60, green: 0.45, blue: 0.20))
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(red: 0.65, green: 0.65, blue: 0.65))
                }
                .buttonStyle(.plain)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Your \(activityName) hangout has passed.")
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                Text("Did \(otherUserName) show up?")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.25, green: 0.25, blue: 0.25))
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
                    .foregroundColor(Color(red: 0.72, green: 0.33, blue: 0.28))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color(red: 0.72, green: 0.33, blue: 0.28).opacity(0.10))
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
        .background(Color(red: 0.99, green: 0.95, blue: 0.86))
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color(red: 0.72, green: 0.55, blue: 0.25))
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
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Suggest a new time")
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                            Text("Your friend will need to re-confirm.")
                                .font(.system(size: 14, design: .rounded))
                                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
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
                        .background(Color.white.opacity(0.85))
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
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
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
                    .foregroundColor(Color(red: 0.25, green: 0.25, blue: 0.25))
                Spacer()
                Button(action: onCancel) {
                    HStack(spacing: 3) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .semibold))
                        Text("Cancel")
                            .font(.system(size: 12, weight: .medium, design: .rounded))
                    }
                    .foregroundColor(Color(red: 0.72, green: 0.33, blue: 0.28))
                }
                .buttonStyle(.plain)
            }

            if let date = plan.confirmedDate {
                HStack(spacing: 4) {
                    Image(systemName: "clock.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    Text(formattedPlanDate(date))
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                }
            }

            if let location = plan.location, !location.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Color.appPrimary)
                    Text(location)
                        .font(.system(size: 13, design: .rounded))
                        .foregroundColor(Color(red: 0.45, green: 0.45, blue: 0.45))
                }
            }
        }
        .padding(14)
        .background(Color(red: 0.93, green: 0.97, blue: 0.93))
        .cornerRadius(12)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color(red: 0.30, green: 0.58, blue: 0.35))
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
