//
//  GroupPlansViewModel.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore

/// Drives the Upcoming tab and group plan detail view: a live listener over every active
/// group plan the current user hosts or is invited to, plus a small user-profile cache for
/// display (host name, invitee names/photos).
@Observable
final class GroupPlansViewModel {
    var groupPlans: [GroupPlan] = []
    var userCache: [String: FirebaseUser] = [:]
    var isLoading = false
    var errorMessage: String?

    private var listener: ListenerRegistration?
    private let authService = FirebaseAuthService.shared
    private let firestoreService = FirestoreService.shared

    var currentUserID: String? { authService.currentUserID }

    deinit {
        listener?.remove()
    }

    func startListening() {
        guard let userID = currentUserID else { return }
        listener?.remove()
        isLoading = true
        listener = GroupPlansService.shared.listenToGroupPlans(for: userID) { [weak self] plans in
            Task { @MainActor in
                guard let self else { return }
                self.groupPlans = plans
                self.isLoading = false
                await self.loadMissingUsers()
            }
        }
    }

    func stopListening() {
        listener?.remove()
        listener = nil
    }

    /// Active group plans the user hosts or is invited to, with a future date, soonest first.
    var upcomingPlans: [GroupPlan] {
        guard let userID = currentUserID else { return [] }
        let now = Date()
        return groupPlans
            .filter { $0.status == .active && $0.date > now && ($0.isHost(userID: userID) || $0.isInvitee(userID: userID)) }
            .sorted { $0.date < $1.date }
    }

    func hostName(for plan: GroupPlan) -> String {
        userCache[plan.hostID]?.displayName ?? "…"
    }

    func inviteeName(for userID: String) -> String {
        userCache[userID]?.displayName ?? "…"
    }

    /// "Hosting" / "Invited" / "Going" / "Can't make it" for the current user on this plan.
    func myStatusText(for plan: GroupPlan) -> String {
        guard let userID = currentUserID else { return "" }
        if plan.isHost(userID: userID) { return "Hosting" }
        switch plan.response(for: userID) {
        case .going: return "Going"
        case .cantMake: return "Can't make it"
        case .invited, .none: return "Invited"
        }
    }

    private func loadMissingUsers() async {
        let neededIDs = Set(groupPlans.flatMap { [$0.hostID] + $0.inviteeIDs }).subtracting(userCache.keys)
        guard !neededIDs.isEmpty else { return }
        for id in neededIDs {
            if let user = try? await firestoreService.fetchUser(userID: id) {
                userCache[id] = user
            }
        }
    }

    @discardableResult
    func respond(to plan: GroupPlan, response: GroupPlanResponse) async -> Bool {
        guard let userID = currentUserID else { return false }
        do {
            try await GroupPlansService.shared.setResponse(planID: plan.id, userID: userID, response: response)
            return true
        } catch {
            errorMessage = "Couldn't update your response. Please try again."
            return false
        }
    }

    @discardableResult
    func cancel(_ plan: GroupPlan) async -> Bool {
        do {
            try await GroupPlansService.shared.cancelPlan(planID: plan.id)
            return true
        } catch {
            errorMessage = "Couldn't cancel the plan. Please try again."
            return false
        }
    }
}
