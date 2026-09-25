package com.georgeappdev.atxfriends.domain.matching

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import com.georgeappdev.atxfriends.data.model.Match
import java.time.Instant

/** What a Yay/Nay write changes, worked out from the latest copy of the match. */
data class DecisionWrite(
    val update: DocumentUpdate,
    val isMutualAfter: Boolean,
    /** isMutualMatch goes false → true with this write (this fires onNewMutualMatch). */
    val completesMutualMatch: Boolean,
)

/**
 * Port of iOS `Match.setDecision` + `FirestoreService.updateMatch` (spec §3, §4).
 *
 * - Only the deciding user's field changes (`user1Decision` or `user2Decision`).
 * - `isMutualMatch` is true exactly when both decisions exist and both are Yay.
 * - `updatedAt` is the client's clock as a Timestamp, like iOS `Timestamp(date: Date())`.
 * - Nay is just a recorded `false`; matches are never deleted (rules forbid it).
 *
 * Rules allow a match update to touch only [ALLOWED_FIELDS].
 */
object MatchDecision {
    val ALLOWED_FIELDS = setOf(
        MatchFields.USER1_DECISION,
        MatchFields.USER2_DECISION,
        MatchFields.IS_MUTUAL_MATCH,
        MatchFields.UPDATED_AT,
    )

    /** @throws IllegalArgumentException if [myID] isn't in the match. */
    fun write(latest: Match, myID: String, yay: Boolean, now: Instant): DecisionWrite {
        val myField = when (myID) {
            latest.user1ID -> MatchFields.USER1_DECISION
            latest.user2ID -> MatchFields.USER2_DECISION
            else -> throw IllegalArgumentException("User isn't part of match ${latest.id}")
        }
        val user1 = if (myField == MatchFields.USER1_DECISION) yay else latest.user1Decision
        val user2 = if (myField == MatchFields.USER2_DECISION) yay else latest.user2Decision
        val mutual = user1 != null && user2 != null && user1 && user2

        val update = DocumentUpdate.Builder()
            .put(myField, yay)
            .put(MatchFields.IS_MUTUAL_MATCH, mutual)
            .put(MatchFields.UPDATED_AT, now)
            .build()
        return DecisionWrite(update, isMutualAfter = mutual, completesMutualMatch = mutual && !latest.isMutualMatch)
    }
}
