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

    /// The current user's own profile, loaded once for the open-slot generator.
    var myProfile: FirebaseUser?

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
        Task { @MainActor in await self.loadMyProfileIfNeeded() }
    }

    func stopListening() {
        listener?.remove()
        listener = nil
    }

    /// Loads the current user's own profile once, for the open-slot generator.
    @MainActor
    func loadMyProfileIfNeeded() async {
        guard myProfile == nil, let userID = currentUserID else { return }
        myProfile = try? await firestoreService.fetchUser(userID: userID)
    }

    /// Active group plans the user hosts or is invited to, with a future date, soonest first.
    var upcomingPlans: [GroupPlan] {
        guard let userID = currentUserID else { return [] }
        let now = Date()
        return groupPlans
            .filter { $0.status == .active && $0.date > now && ($0.isHost(userID: userID) || $0.isInvitee(userID: userID)) }
            .sorted { $0.date < $1.date }
    }

    /// Upcoming plans later today — shown in the "Today" section above the week strip, which
    /// itself starts tomorrow. (Before this section existed, an invite for later today — the
    /// composer's default time — was saved but shown nowhere.)
    var todayPlans: [GroupPlan] {
        Self.todayPlans(upcomingPlans, now: Date(), calendar: .current)
    }

    /// Upcoming plans after the week strip's last day — shown in the "Later" section.
    var laterPlans: [GroupPlan] {
        Self.laterPlans(upcomingPlans, now: Date(), calendar: .current)
    }

    /// Plans (already filtered to upcoming) on today's calendar day.
    static func todayPlans(_ plans: [GroupPlan], now: Date, calendar: Calendar) -> [GroupPlan] {
        plans.filter { calendar.isDate($0.date, inSameDayAs: now) }
    }

    /// Plans (already filtered to upcoming) on or after the start of today + 8 days — past the
    /// strip, which runs tomorrow through today + 7.
    static func laterPlans(_ plans: [GroupPlan], now: Date, calendar: Calendar) -> [GroupPlan] {
        guard let afterStrip = calendar.date(byAdding: .day, value: 8, to: calendar.startOfDay(for: now)) else { return [] }
        return plans.filter { $0.date >= afterStrip }
    }

    /// Up to one open-slot ghost suggestion per day, for tomorrow through today + 6 (the
    /// window ends at the start of the strip's 7th day), keyed by the start of that calendar day. Days that already have a real
    /// upcoming plan are skipped by the caller (see `hasPlan(on:)`); this generator doesn't
    /// know about that — it only avoids exact plan-time collisions via existingPlanStarts.
    /// Activities are ordered to prefer the `.planned` tier (see ActivityCategories) so
    /// Upcoming's suggestions stay disjoint from Today's `.spontaneous`-preferring ones
    /// whenever there's enough variety in the user's profile.
    var openSlotsByDay: [Date: OpenSlot] {
        guard let myProfile else { return [:] }
        let cal = Calendar.current
        let now = Date()
        guard
            let start = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: now)),
            let end = cal.date(byAdding: .day, value: 7, to: cal.startOfDay(for: now))
        else { return [:] }

        let existingStarts = upcomingPlans.map { $0.date }
        let slots = OpenSlotGenerator.generate(
            daySlotCombos: myProfile.daySlotCombos,
            activities: OpenSlotGenerator.activities(from: myProfile.activities, preferring: .planned),
            from: start,
            through: end,
            existingPlanStarts: existingStarts,
            maxCount: 7
        )

        var result: [Date: OpenSlot] = [:]
        for slot in slots {
            let day = cal.startOfDay(for: slot.start)
            if result[day] == nil {
                result[day] = slot
            }
        }
        return result
    }

    /// True if any active upcoming plan falls on the same calendar day as `date`.
    func hasPlan(on date: Date) -> Bool {
        let cal = Calendar.current
        return upcomingPlans.contains { cal.isDate($0.date, inSameDayAs: date) }
    }

    /// Active upcoming plans on the same calendar day as `date`.
    func plans(on date: Date) -> [GroupPlan] {
        let cal = Calendar.current
        return upcomingPlans.filter { cal.isDate($0.date, inSameDayAs: date) }
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
