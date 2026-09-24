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
/// Drives red dots on tab bar items, conversation rows, and plan cards — and is
/// the single source of truth for the app badge count (see recomputeBadge).
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
    private var currentUserID: String?

    /// Latest unread-message document count from listenForUnreadMessages.
    /// Combined with unreadPlanIDs.count in recomputeBadge — the single
    /// formula for "the badge number," recomputed from scratch on every
    /// listener fire rather than incremented/decremented, so it can't drift.
    private var unreadMessageDocCount: Int = 0

    private init() {}

    // MARK: - Start / Stop

    /// Call when user signs in
    func startListening(userID: String) {
        stopListening()
        currentUserID = userID
        listenForUnreadMessages(userID: userID)
        listenForUnreadPlans(userID: userID)
    }

    /// Call when user signs out
    func stopListening() {
        messagesListener?.remove()
        plansListener?.remove()
        messagesListener = nil
        plansListener = nil
        currentUserID = nil
        unreadMessageDocCount = 0
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
                    self.unreadMessageDocCount = snapshot.documents.count
                    await self.recomputeBadge(userID: userID)
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
                    // Compare against the enum, not the Cloud Function's old string
                    // literal — PlanStatus.counterProposed's rawValue is "counter",
                    // not "counterProposed". See Plan.swift.
                    let status = (data["status"] as? String).flatMap(PlanStatus.init(rawValue:))
                    let isViewed = data["isViewed"] as? Bool ?? false
                    if (status == .pending || status == .counterProposed) && !isViewed {
                        planIDs.insert(doc.documentID)
                    }
                }
                Task { @MainActor in
                    self.unreadPlanIDs = planIDs
                    self.hasUnreadPlans = !planIDs.isEmpty
                    await self.recomputeBadge(userID: userID)
                }
            }
    }

    // MARK: - Badge

    /// The one formula for the app badge: unread messages + plans needing
    /// attention. Sets the on-device badge immediately and mirrors the same
    /// total into `users/{uid}.unreadCount`, so the number the server attaches
    /// to a push's APNs payload (while this device is offline) is corrected
    /// the moment the app is foregrounded and these listeners fire again.
    private func recomputeBadge(userID: String) async {
        guard userID == currentUserID else { return }
        let total = unreadMessageDocCount + unreadPlanIDs.count
        NotificationManager.shared.setBadge(count: total)
        try? await FirestoreService.shared.setUnreadCount(userID: userID, count: total)
    }

    // MARK: - Mark Read Actions

    /// Call when user opens a conversation. Marks messages read; the messages
    /// listener above re-fires with the smaller unread set and recomputes the
    /// badge from scratch, so no manual decrement is needed here.
    func markConversationRead(matchID: String, userID: String) {
        Task {
            do {
                try await MessagingService.shared.markAllAsRead(matchID: matchID, for: userID)
                print("✅ Marked conversation \(matchID) read")
            } catch {
                print("❌ Error marking conversation read: \(error)")
            }
        }
    }

    /// Call when user views a plan detail AND takes action (confirm/decline/counter).
    /// Also call for outgoing plans when the proposer views a counter-proposal.
    /// Marks the plan viewed; the plans listener above re-fires with the smaller
    /// unread set and recomputes the badge from scratch.
    func markPlanViewed(planID: String, userID: String) {
        Task {
            do {
                guard unreadPlanIDs.contains(planID) else { return }
                try await FirestoreService.shared.markPlanViewed(planID: planID)
                print("✅ Marked plan \(planID) viewed")
            } catch {
                print("❌ Error marking plan viewed: \(error)")
            }
        }
    }
}
