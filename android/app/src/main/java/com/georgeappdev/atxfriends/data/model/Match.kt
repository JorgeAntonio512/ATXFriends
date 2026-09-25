package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import java.time.Instant

/** `matches/{matchID}`. A null decision means that user hasn't chosen Yay/Nay yet. */
data class Match(
    val id: String,
    val user1ID: String,
    val user2ID: String,
    val user1Decision: Boolean?,
    val user2Decision: Boolean?,
    val isMutualMatch: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val overlappingActivityNames: List<String>,
    val overlappingDaySlots: List<String>,
    val overlappingCategoryNames: List<String>,
) {
    companion object {
        /** Same required fields as iOS `firestoreDataToMatch`; null (skipped) if any is missing. */
        fun fromFirestore(id: String, data: Map<String, Any?>): Match? {
            val r = DocReader(data)
            return Match(
                id = id,
                user1ID = r.string(MatchFields.USER1_ID) ?: return null,
                user2ID = r.string(MatchFields.USER2_ID) ?: return null,
                isMutualMatch = r.bool(MatchFields.IS_MUTUAL_MATCH) ?: return null,
                createdAt = r.instant(MatchFields.CREATED_AT) ?: return null,
                updatedAt = r.instant(MatchFields.UPDATED_AT) ?: return null,
                overlappingActivityNames = r.stringList(MatchFields.OVERLAPPING_ACTIVITY_NAMES) ?: return null,
                overlappingDaySlots = r.stringList(MatchFields.OVERLAPPING_DAY_SLOTS) ?: return null,
                user1Decision = r.bool(MatchFields.USER1_DECISION),
                user2Decision = r.bool(MatchFields.USER2_DECISION),
                // Absent on matches created before the field existed.
                overlappingCategoryNames = r.stringList(MatchFields.OVERLAPPING_CATEGORY_NAMES).orEmpty(),
            )
        }
    }
}
