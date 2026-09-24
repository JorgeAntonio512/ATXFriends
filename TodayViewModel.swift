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
    /// Non-nil briefly after a successful post (ghost card or scratch); shown as a toast.
    var postToast: String?

    /// Cached display info for plan creators, keyed by userID.
    var posterInfo: [String: PosterInfo] = [:]

    /// The current user's own profile, loaded once for the open-slot generator. Nil until
    /// loaded or if the user has no profile.
    var myProfile: FirebaseUser?

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

    /// Up to 3 ghost-card suggestions, next 24 hours only. Own usual timeslots first, then
    /// the fixed fallback clock times fill any remaining spots — so Today always has cards
    /// even when none of the user's usual slots land in the next day. Activities are ordered
    /// to prefer the `.spontaneous` tier (see ActivityCategories) so Today's suggestions stay
    /// disjoint from Upcoming's `.planned`-preferring ones whenever there's enough variety.
    /// Purely derived from data already loaded — no Firestore access happens here.
    var rankedOpenSlots: [OpenSlotGenerator.RankedOpenSlot] {
        guard let myProfile else { return [] }
        let myExistingStarts = openPlans
            .filter { $0.creatorID == currentUserID }
            .map { $0.scheduledTime }
        return OpenSlotGenerator.generateForToday(
            daySlotCombos: myProfile.daySlotCombos,
            activities: OpenSlotGenerator.activities(from: myProfile.activities, preferring: .spontaneous),
            from: Date(),
            existingPlanStarts: myExistingStarts,
            maxCount: 3
        )
    }

    /// True only when every card currently shown came from the fallback clock times —
    /// drives the "Free in the next day?" vs. "Your open slots" section header.
    var allOpenSlotsAreFallback: Bool {
        let ranked = rankedOpenSlots
        return !ranked.isEmpty && ranked.allSatisfy { $0.source == .fallback }
    }

    /// The user's next usual (profile timeslot) occurrence within the next 7 days,
    /// regardless of whether it's postable — backs the "Your next usual slot" link.
    var nextUsualSlot: OpenSlot? {
        guard let myProfile else { return nil }
        let now = Date()
        return OpenSlotGenerator.nextUsualOccurrence(
            daySlotCombos: myProfile.daySlotCombos,
            from: now,
            through: now.addingTimeInterval(7 * 24 * 60 * 60)
        )
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
        await loadMyProfileIfNeeded()
    }

    /// Loads the current user's own profile once, for the open-slot generator.
    @MainActor
    func loadMyProfileIfNeeded() async {
        guard myProfile == nil, let userID = currentUserID else { return }
        myProfile = try? await firestoreService.fetchUser(userID: userID)
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
            postToast = "Posted!"
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
