//
//  TodayViewModel.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore

@Observable
final class TodayViewModel {

    // MARK: - State

    var openPlans: [TodayPlan] = []
    var isLoading = false
    var errorMessage: String?
    /// nil = show all; non-nil = filter feed to this activity name
    var activityFilter: String?
    /// Non-nil when the current user's own plan was just claimed; shown as a banner.
    var claimedPlanBanner: String?

    /// Cached display info for plan creators, keyed by userID.
    var posterInfo: [String: PosterInfo] = [:]

    struct PosterInfo {
        let displayName: String
        let showUpMeter: String
    }

    // MARK: - Services

    private let service = TodayPlanService.shared
    private let firestoreService = FirestoreService.shared
    private let authService = FirebaseAuthService.shared
    private var planListener: ListenerRegistration?

    // MARK: - Derived

    var currentUserID: String? { authService.currentUserID }

    /// Distinct activity names present in the live feed, for the filter chip bar.
    var availableActivities: [String] {
        let names = openPlans.filter { !$0.isExpired }.map { $0.activity.name }
        return Array(Set(names)).sorted()
    }

    /// The visible feed after two filters:
    /// 1. Server query already excluded claimed plans and out-of-today times.
    /// 2. Client guard removes anything that expired since the last listener snapshot.
    /// 3. Activity filter chip (UI only — quantity is small enough for client-side).
    var filteredPlans: [TodayPlan] {
        let live = openPlans.filter { !$0.isExpired }
        guard let filter = activityFilter else { return live }
        return live.filter { $0.activity.name == filter }
    }

    // MARK: - Load

    @MainActor
    func loadOpenPlans() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let plans = try await service.fetchOpenPlans()
            openPlans = plans
            await prefetchPosterInfo(for: plans)
        } catch {
            errorMessage = "Could not load plans: \(error.localizedDescription)"
        }
    }

    // MARK: - Listener

    func setupListener() {
        planListener?.remove()
        planListener = service.listenToOpenPlans { [weak self] newPlans in
            Task { @MainActor [weak self] in
                guard let self else { return }

                // Detect when one of the poster's own open plans disappears from the feed.
                // The listener only returns status==open docs, so a non-expired plan
                // that vanishes must have been claimed by someone else.
                if let myID = authService.currentUserID {
                    let justClaimed = openPlans.filter { old in
                        old.creatorID == myID &&
                        !old.isExpired &&
                        !newPlans.contains(where: { $0.id == old.id })
                    }
                    if let plan = justClaimed.first {
                        claimedPlanBanner = "Someone claimed your \(plan.activity.name) plan! 🎉"
                    }
                }

                openPlans = newPlans
                await prefetchPosterInfo(for: newPlans)
            }
        }
    }

    func removeListener() {
        planListener?.remove()
        planListener = nil
    }

    // MARK: - Claim

    /// Claims the plan atomically and navigates the claimer to the new chat thread.
    /// Returns true on success, false on failure (e.g. already claimed by someone else).
    @MainActor
    func claimPlan(_ plan: TodayPlan) async -> Bool {
        guard let myID = authService.currentUserID, plan.creatorID != myID else { return false }
        do {
            let matchID = try await service.claimTodayPlan(
                planID: plan.id,
                claimerID: myID,
                creatorID: plan.creatorID,
                activity: plan.activity,
                scheduledTime: plan.scheduledTime
            )
            // Optimistic removal; the listener will confirm on the next snapshot.
            openPlans.removeAll { $0.id == plan.id }

            // Switch to Messages and open the thread with the plan poster.
            let creatorName = posterInfo[plan.creatorID]?.displayName ?? ""
            NotificationCenter.default.post(
                name: .navigateToMatchThread,
                object: nil,
                userInfo: [
                    "matchID": matchID,
                    "otherUserID": plan.creatorID,
                    "otherUserName": creatorName
                ]
            )
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    // MARK: - Create

    @MainActor
    func createPlan(
        activity: Activity,
        scheduledTime: Date,
        note: String?,
        location: String?,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil
    ) async -> Bool {
        guard let myID = authService.currentUserID else { return false }
        let plan = TodayPlan(
            creatorID: myID,
            activity: activity,
            scheduledTime: scheduledTime,
            note: (note?.isEmpty == false) ? note : nil,
            location: (location?.isEmpty == false) ? location : nil,
            locationName: locationName,
            locationLatitude: locationLatitude,
            locationLongitude: locationLongitude
        )
        do {
            try await service.createTodayPlan(plan)
            // Optimistic insert so the user sees their new post immediately
            openPlans.append(plan)
            openPlans.sort { $0.scheduledTime < $1.scheduledTime }
            return true
        } catch {
            errorMessage = "Could not post plan: \(error.localizedDescription)"
            return false
        }
    }

    // MARK: - Poster Info Cache

    @MainActor
    private func prefetchPosterInfo(for plans: [TodayPlan]) async {
        let unknownIDs = Set(plans.map { $0.creatorID }).filter { posterInfo[$0] == nil }
        for userID in unknownIDs {
            if let user = try? await firestoreService.fetchUser(userID: userID) {
                posterInfo[userID] = PosterInfo(
                    displayName: user.displayName,
                    showUpMeter: user.showUpMeter
                )
            }
        }
    }
}
