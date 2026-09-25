package com.georgeappdev.atxfriends.domain.today

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.data.repository.PlanWrites
import java.time.Instant

/** Someone else claimed the plan first (iOS NSError code 409). The message is iOS's, verbatim. */
class PlanAlreadyClaimedException : Exception("This plan is no longer available — someone got there first!")

/** How the claim changes the pair's match. */
sealed interface MatchUpsert {
    val matchID: String

    /** A match already exists for the pair — upgrade it to mutual in place. */
    data class Upgrade(override val matchID: String, val update: DocumentUpdate) : MatchUpsert

    /** No match yet — create one at the deterministic ID. */
    data class Create(override val matchID: String, val doc: NewDocument) : MatchUpsert
}

/** The Firestore operations a claim needs (a seam so the flow and the race can be unit-tested). */
interface TodayClaimStore {
    /**
     * Runs one transaction on `todayPlans/{planID}`: reads it, calls [check] with its current
     * `status` (null if missing), and — only if [check] returns normally — applies [claim].
     * Retried by Firestore on contention, so [check] sees the latest status each attempt.
     */
    suspend fun claimInTransaction(planID: String, check: (status: String?) -> Unit, claim: DocumentUpdate)

    /** The ID of the pair's match with exactly `user1ID == user1 && user2ID == user2`, or null. */
    suspend fun findMatchID(user1ID: String, user2ID: String): String?

    /** Writes the match change and the new confirmed plan together, atomically. */
    suspend fun commitMatchAndPlan(match: MatchUpsert, plan: NewDocument)
}

/**
 * Port of iOS `TodayPlanService.claimTodayPlan`, same steps in the same order:
 *  1. Transaction on the Today plan: fail with [PlanAlreadyClaimedException] unless its status
 *     is exactly "open"; otherwise set `status: claimed, claimerID, updatedAt`.
 *  2. Look up the pair's match with `user1ID == min(uid) && user2ID == max(uid)`, limit 1 (a
 *     query, so legacy UUID-named match docs are found too).
 *  3. Existing match → set both decisions true + `isMutualMatch` + `updatedAt`. None → create a
 *     mutual match at `{min}_{max}` with the post's activity as the only overlapping activity.
 *  4. Write the pre-confirmed Plan (claimer = proposer, creator = receiver) so the thread opens
 *     with the pinned plan card.
 *
 * One Android difference: steps 3 and 4 commit as a single batch, so a failure can't leave a
 * match without its plan. Steps 1–2 can't join that batch: Firestore transactions can't run
 * queries, and reading a match doc that doesn't exist yet is denied by the matches read rule.
 * Returns the match ID so the caller can open the thread.
 */
object TodayClaim {

    /** The transaction's only check, exactly as iOS: status must be the string "open". */
    fun checkClaimable(status: String?) {
        if (status != TodayPlanStatus.OPEN.raw) throw PlanAlreadyClaimedException()
    }

    suspend fun claim(
        store: TodayClaimStore,
        planID: String,
        claimerID: String,
        creatorID: String,
        activity: Activity,
        scheduledTime: Instant,
        clock: () -> Instant = Instant::now,
    ): String {
        store.claimInTransaction(planID, ::checkClaimable, PlanWrites.todayPlanClaim(claimerID, clock()))

        val user1 = minOf(creatorID, claimerID)
        val user2 = maxOf(creatorID, claimerID)
        val now = clock()

        val existing = store.findMatchID(user1, user2)
        val match = if (existing != null) {
            MatchUpsert.Upgrade(existing, PlanWrites.matchUpgradeToMutual(now))
        } else {
            val id = PlanWrites.matchID(user1, user2)
            MatchUpsert.Create(id, PlanWrites.newMutualMatch(user1, user2, activity.name, now))
        }

        val plan = PlanWrites.claimedPlan(match.matchID, claimerID, creatorID, activity, scheduledTime, now)
        store.commitMatchAndPlan(match, plan)
        return match.matchID
    }
}
