//
//  UnreadState.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/30/26.
//

import Foundation
import FirebaseFirestore
import FirebaseAuth
import Combine

/// Shared observable that tracks in-app unread state across Messages and Plans.
/// Drives red dots on tab bar items, conversation rows, and plan cards.
@MainActor
final class UnreadState: ObservableObject {

    // MARK: - Singleton

    static let shared = UnreadState()

    // MARK: - Published State

    /// Whether any conversation has unread messages (drives Messages tab dot)
    @Published var hasUnreadMessages: Bool = false

    /// Whether any plan needs attention (drives Plans tab dot)
    @Published var hasUnreadPlans: Bool = false

    /// Match IDs of conversations with unread messages
    @Published var unreadMatchIDs: Set<String> = []

    /// Plan IDs that have not been viewed/acted on
    @Published var unreadPlanIDs: Set<String> = []

    // MARK: - Private

    private let db = Firestore.firestore()
    private var messagesListener: ListenerRegistration?
    private var plansListener: ListenerRegistration?

    private init() {}

    // MARK: - Start / Stop

    /// Call when user signs in
    func startListening(userID: String) {
        stopListening()
        listenForUnreadMessages(userID: userID)
        listenForUnreadPlans(userID: userID)
    }

    /// Call when user signs out
    func stopListening() {
        messagesListener?.remove()
        plansListener?.remove()
        messagesListener = nil
        plansListener = nil
        unreadMatchIDs = []
        unreadPlanIDs = []
        hasUnreadMessages = false
        hasUnreadPlans = false
    }

    // MARK: - Messages

    private func listenForUnreadMessages(userID: String) {
        messagesListener = db.collection("messages")
            .whereField("receiverID", isEqualTo: userID)
            .whereField("isRead", isEqualTo: false)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot else { return }
                var matchIDs: Set<String> = []
                for doc in snapshot.documents {
                    if let matchID = doc.data()["matchID"] as? String {
                        matchIDs.insert(matchID)
                    }
                }
                Task { @MainActor in
                    self.unreadMatchIDs = matchIDs
                    self.hasUnreadMessages = !matchIDs.isEmpty
                    // ← ADD THIS: sync badge with total unread message count
                    NotificationManager.shared.setBadge(count: snapshot.documents.count)
                }
            }
    }

    // MARK: - Plans

    private func listenForUnreadPlans(userID: String) {
        // Plans needing attention = incoming plans that are pending or counter-proposed
        // AND outgoing plans that received a counter-proposal (receiverID = me, status = counterProposed)
        plansListener = db.collection("plans")
            .whereField("receiverID", isEqualTo: userID)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self, let snapshot else { return }
                var planIDs: Set<String> = []
                for doc in snapshot.documents {
                    let data = doc.data()
                    let status = data["status"] as? String ?? ""
                    let isViewed = data["isViewed"] as? Bool ?? false
                    // Needs attention if pending/counter-proposed and not yet viewed
                    if (status == "pending" || status == "counterProposed") && !isViewed {
                        planIDs.insert(doc.documentID)
                    }
                }
                Task { @MainActor in
                    self.unreadPlanIDs = planIDs
                    self.hasUnreadPlans = !planIDs.isEmpty
                }
            }
    }

    // MARK: - Mark Read Actions

    /// Call when user opens a conversation. Marks all messages read and decrements badge.
    func markConversationRead(matchID: String, userID: String) {
        Task {
            do {
                // Get unread count BEFORE marking read so we know how much to decrement
                let count = try await MessagingService.shared.getUnreadCount(matchID: matchID, for: userID)
                guard count > 0 else { return }

                try await MessagingService.shared.markAllAsRead(matchID: matchID, for: userID)

                // Decrement badge by exact unread count
                try await FirestoreService.shared.decrementUnreadCount(userID: userID, by: count)
                let newBadge = try await FirestoreService.shared.getUnreadCount(userID: userID)
                NotificationManager.shared.setBadge(count: max(0, newBadge))

                print("✅ Marked conversation \(matchID) read, decremented badge by \(count)")
            } catch {
                print("❌ Error marking conversation read: \(error)")
            }
        }
    }

    /// Call when user views a plan detail AND takes action (confirm/decline/counter).
    /// Also call for outgoing plans when the proposer views a counter-proposal.
    func markPlanViewed(planID: String, userID: String) {
        Task {
            do {
                // Only decrement if this plan was actually unread
                guard unreadPlanIDs.contains(planID) else { return }

                try await FirestoreService.shared.markPlanViewed(planID: planID)
                try await FirestoreService.shared.decrementUnreadCount(userID: userID, by: 1)
                let newBadge = try await FirestoreService.shared.getUnreadCount(userID: userID)
                NotificationManager.shared.setBadge(count: max(0, newBadge))

                print("✅ Marked plan \(planID) viewed, decremented badge by 1")
            } catch {
                print("❌ Error marking plan viewed: \(error)")
            }
        }
    }
}
