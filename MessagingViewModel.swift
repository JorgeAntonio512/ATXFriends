//
//  MessagingViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseFirestore
import SwiftUI

/// ViewModel for managing messaging threads and conversations
@Observable
final class MessagingViewModel {
    // MARK: - Published State
    
    /// All message threads
    var messageThreads: [MessageThread] = []
    
    /// Messages in the current conversation
    var messages: [Message] = []
    
    /// Current draft message
    var draftMessage: String = ""
    
    /// Error message to display
    var errorMessage: String?
    
    /// Whether data is loading
    var isLoading: Bool = false
    
    /// Whether a message is being sent
    var isSendingMessage: Bool = false

    /// All confirmed upcoming plans for the open thread, sorted soonest first.
    /// Updated in real time by listenToConfirmedPlan — empty when no confirmed plans exist.
    var confirmedPlans: [Plan] = []

    /// IDs of every confirmed plan for the open thread, including past ones.
    /// Updated in real time by listenToConfirmedPlan alongside confirmedPlans — this is the
    /// single source of truth for "is this plan confirmed" used to hide inline proposal cards.
    var confirmedPlanIDs: Set<String> = []

    /// The soonest confirmed upcoming plan; nil when confirmedPlans is empty.
    var pinnedPlan: Plan? { confirmedPlans.first }

    /// Pending TodayPlan awaiting a show-up report from the current user.
    /// Set when the thread opens; cleared after submitting or dismissing.
    var pendingShowUpPlan: TodayPlan?

    /// Show-up reliability meter string for the other participant in the open thread.
    /// Fetched once when the thread opens; defaults to "New" until loaded.
    var otherUserShowUpMeter: String = "New"

    /// Current user's own profile photo, for the pinned-plan card's People row.
    /// Fetched once when the thread opens.
    var currentUserPhotoURL: String? = nil

    /// Non-nil when a show-up report write fails; triggers an alert in MessageThreadView.
    var showUpReportError: String? = nil
    
    // MARK: - Computed Properties
    
    /// All threads
    var allThreads: [MessageThread] {
        messageThreads
    }
    
    // MARK: - Services
    
    private let messagingService = MessagingService.shared
    private let firestoreService = FirestoreService.shared
    private let authService = FirebaseAuthService.shared
    
    /// Current real-time listener
    private var messageListener: ListenerRegistration?
    private var planListener: ListenerRegistration?
    
    // MARK: - Initialization
    
    init() {}
    
    deinit {
        // Clean up listener
        messageListener?.remove()
    }
    
    // MARK: - Load Message Threads
    
    /// Loads all message threads for the current user
    @MainActor
    func fetchThreads() async {
        await loadMessageThreads()
    }
    
    /// Loads all message threads for the current user
    @MainActor
    func loadMessageThreads() async {
        guard let currentUserID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            // Fetch all mutual matches (regular DM threads)
            let matches = try await firestoreService.fetchMatches(for: currentUserID)
            let mutualMatches = matches.filter { $0.isMutualMatch }
            
            var threads: [MessageThread] = []
            
            // 1. Create threads from matches (regular DMs)
            for match in mutualMatches {
                // Get the other user
                guard let otherUserID = match.otherUserID(for: currentUserID),
                      let firebaseUser = try await firestoreService.fetchUser(userID: otherUserID) else {
                    continue
                }
                
                // Convert FirebaseUser to User
                let otherUser = firebaseUser.toUser()
                let showUpMeter = firebaseUser.showUpMeter

                // Get last message
                let lastMessage = try? await messagingService.getLastMessage(for: match.id)
                
                // Get unread count
                let unreadCount = (try? await messagingService.getUnreadCount(matchID: match.id, for: currentUserID)) ?? 0

                // Fetch next confirmed upcoming plan for this match (including one with a
                // pending reschedule request — its original time still stands)
                let matchPlans = try? await PlansService.shared.fetchPlans(forMatch: match.id)
                let upcomingPlan = matchPlans?
                    .filter { $0.isUpcoming() }
                    .min { ($0.standingDate ?? .distantFuture) < ($1.standingDate ?? .distantFuture) }

                // Live-computed from both questionnaires; nil until both users finish all 18.
                let simpaticoScore = try? await SimpaticoService.shared.fetchScore(
                    userID: currentUserID,
                    friendID: otherUserID
                )

                let thread = MessageThread(
                    id: match.id,
                    match: match,
                    otherUser: otherUser,
                    lastMessage: lastMessage,
                    unreadCount: unreadCount,
                    upcomingPlan: upcomingPlan,
                    simpaticoScore: simpaticoScore,
                    showUpMeter: showUpMeter
                )

                threads.append(thread)
            }
            
            // Threads with an upcoming confirmed plan sort first (soonest date first).
            // Threads without a plan sort by most-recent message, same as before.
            messageThreads = threads.sorted { t1, t2 in
                switch (t1.upcomingPlan?.confirmedDate, t2.upcomingPlan?.confirmedDate) {
                case let (.some(d1), .some(d2)):
                    return d1 < d2
                case (.some, .none):
                    return true
                case (.none, .some):
                    return false
                case (.none, .none):
                    let d1 = t1.lastMessage?.sentAt ?? t1.match?.createdAt ?? .distantPast
                    let d2 = t2.lastMessage?.sentAt ?? t2.match?.createdAt ?? .distantPast
                    return d1 > d2
                }
            }
            
            // Debug: Print all threads and their classification
            print("📊 DEBUG: Total threads loaded: \(messageThreads.count)")
            for thread in messageThreads {
                print("   Thread ID: \(thread.id)")
                print("      - Other user: \(thread.otherUser.displayName)")
                print("      - Has match: \(thread.match != nil)")
                print("      ---")
            }
            
        } catch {
            errorMessage = "Failed to load messages: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Load Messages for Thread
    
    /// Loads messages for a specific match and sets up real-time listening
    @MainActor
    func loadMessages(for matchID: String) async {
        guard let currentUserID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        print("🔵 DEBUG: ========================================")
        print("🔵 DEBUG: MessagingViewModel.loadMessages called")
        print("🔵 DEBUG: matchID parameter: \(matchID)")
        print("🔵 DEBUG: ========================================")
        
        isLoading = true
        
        do {
            // Fetch initial messages
            messages = try await messagingService.fetchMessages(for: matchID)
            print("🔵 DEBUG: Fetched \(messages.count) initial messages")
            
            // Mark all as read
            try? await messagingService.markAllAsRead(matchID: matchID, for: currentUserID)
            
            // Set up real-time listener
            messageListener?.remove()
            print("🔵 DEBUG: Setting up Firestore listener for matchID: \(matchID)")
            messageListener = messagingService.listenToMessages(for: matchID) { [weak self] newMessages in
                Task { @MainActor in
                    print("🟢 DEBUG: Listener fired! Received \(newMessages.count) messages")
                    self?.messages = newMessages
                    print("🟢 DEBUG: Messages array updated. Current count: \(self?.messages.count ?? 0)")
                    
                    // Mark new messages as read
                    try? await self?.messagingService.markAllAsRead(matchID: matchID, for: currentUserID)
                }
            }
            
            isLoading = false
            print("🔵 DEBUG: loadMessages completed. Listener is active.")
        } catch {
            errorMessage = "Failed to load messages: \(error.localizedDescription)"
            print("🔴 DEBUG: Error loading messages: \(error)")
            isLoading = false
        }
    }
    
    // MARK: - Send Message
    
    /// Sends a message in the current conversation
    @MainActor
    func sendMessage(matchID: String, receiverID: String) async {
        guard let senderID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        guard !draftMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        
        let messageText = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        draftMessage = "" // Clear immediately for better UX
        
        isSendingMessage = true
        defer { isSendingMessage = false }
        
        do {
            try await messagingService.sendMessage(
                text: messageText,
                matchID: matchID,
                senderID: senderID,
                receiverID: receiverID
            )
            
            // Message will appear via real-time listener
        } catch {
            errorMessage = "Failed to send message: \(error.localizedDescription)"
            draftMessage = messageText // Restore message on error
        }
    }
    
    // MARK: - Plan Proposals

    /// Creates a Plan doc and posts a planProposal message into the thread.
    @MainActor
    func proposePlan(
        matchID: String,
        receiverID: String,
        activityName: String,
        location: String? = nil,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        scheduledDate: Date
    ) async throws {
        guard let senderID = authService.currentUserID else {
            throw NSError(domain: "MessagingViewModel", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "Not signed in"])
        }

        let activity = Activity(
            id: UUID().uuidString,
            name: activityName,
            isUserAdded: false,
            createdAt: Date()
        )

        let plan = Plan(
            matchID: matchID,
            proposerID: senderID,
            receiverID: receiverID,
            activity: activity,
            location: location,
            locationName: locationName,
            locationLatitude: locationLatitude,
            locationLongitude: locationLongitude,
            proposedDates: [scheduledDate]
        )

        try await PlansService.shared.createPlan(plan)

        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .medium
        dateFormatter.timeStyle = .short
        let summary = "Proposed \(activityName) · \(dateFormatter.string(from: scheduledDate))"

        try await messagingService.sendPlanProposal(
            planID: plan.id,
            summary: summary,
            matchID: matchID,
            senderID: senderID,
            receiverID: receiverID
        )
    }

    // MARK: - Cleanup

    /// Removes the real-time listener when leaving a conversation
    func stopListening() {
        messageListener?.remove()
        messageListener = nil
        planListener?.remove()
        planListener = nil
        messages = []
        confirmedPlans = []
        confirmedPlanIDs = []
        pendingShowUpPlan = nil
    }

    // MARK: - Pinned Plan Listener

    /// Starts a real-time listener that keeps pinnedPlan and confirmedPlanIDs in sync with
    /// the plans for the given match. Call this after loadMessages and stop via stopListening.
    func listenToConfirmedPlan(forMatch matchID: String) {
        print("📌 MessagingViewModel.listenToConfirmedPlan: starting for match \(matchID)")
        planListener?.remove()
        planListener = PlansService.shared.listenToConfirmedPlan(forMatch: matchID) { [weak self] plans, confirmedIDs in
            Task { @MainActor in
                print("📌 MessagingViewModel: confirmedPlans updated → \(plans.map { $0.activity.name })")
                withAnimation {
                    self?.confirmedPlans = plans
                    self?.confirmedPlanIDs = confirmedIDs
                }
            }
        }
    }
    
    // MARK: - Reschedule Requests

    /// Non-nil when accepting/declining a reschedule fails; triggers an alert in MessageThreadView.
    var rescheduleError: String? = nil

    /// Accepts the pending reschedule request on `plan` (the listener then delivers the
    /// plan confirmed at the new time).
    @MainActor
    func acceptReschedule(_ plan: Plan) async {
        do {
            try await PlansService.shared.acceptReschedule(plan)
        } catch {
            rescheduleError = "Couldn't accept the new time. Check your connection and try again."
            print("❌ MessagingViewModel: acceptReschedule failed: \(error)")
        }
    }

    /// Declines the pending reschedule request on `plan`; the plan stays at its original time.
    @MainActor
    func declineReschedule(_ plan: Plan) async {
        do {
            try await PlansService.shared.declineReschedule(plan)
        } catch {
            rescheduleError = "Couldn't decline the new time. Check your connection and try again."
            print("❌ MessagingViewModel: declineReschedule failed: \(error)")
        }
    }

    // MARK: - Show-Up Report

    /// Fetches the other participant's show-up meter for the open thread.
    /// Called once when the thread opens.
    @MainActor
    func loadOtherUserShowUpMeter(otherUserID: String) async {
        if let firebaseUser = try? await FirestoreService.shared.fetchUser(userID: otherUserID) {
            otherUserShowUpMeter = firebaseUser.showUpMeter
        }
    }

    /// Fetches the current user's own profile photo for the pinned-plan card's People row.
    @MainActor
    func loadCurrentUserPhoto() async {
        guard let currentUserID = authService.currentUserID else { return }
        if let firebaseUser = try? await FirestoreService.shared.fetchUser(userID: currentUserID) {
            currentUserPhotoURL = firebaseUser.photoURLs.first
        }
    }

    /// Loads the pending TodayPlan show-up report for the open thread, if any.
    /// Called once when the thread opens; each participant's result is independent.
    @MainActor
    func loadPendingShowUpReport(otherUserID: String) async {
        guard let currentUserID = authService.currentUserID else { return }
        pendingShowUpPlan = try? await TodayPlanService.shared.fetchPendingShowUpReport(
            currentUserID: currentUserID,
            otherUserID: otherUserID
        )
    }

    /// Submits a thumbs-up or thumbs-down for the pending show-up plan.
    /// Only clears the prompt on success; on failure sets showUpReportError and leaves
    /// the prompt visible so the user can retry. Returns true on success.
    @MainActor
    @discardableResult
    func submitShowUpReport(thumbsUp: Bool) async -> Bool {
        guard let plan = pendingShowUpPlan,
              let currentUserID = authService.currentUserID,
              let reportedUserID = plan.otherUserID(for: currentUserID),
              !reportedUserID.isEmpty else { return false }
        do {
            try await TodayPlanService.shared.submitShowUpReport(
                planID: plan.id,
                reporterID: currentUserID,
                didShowUp: thumbsUp,
                reportedUserID: reportedUserID
            )
            pendingShowUpPlan = nil
            NotificationCenter.default.post(name: .showUpReportSubmitted, object: nil)
            return true
        } catch {
            showUpReportError = "Couldn't save your report. Check your connection and try again."
            print("❌ MessagingViewModel: submitShowUpReport failed: \(error)")
            return false
        }
    }

    // MARK: - Utility

    /// Clears error message
    func clearError() {
        errorMessage = nil
    }
}
