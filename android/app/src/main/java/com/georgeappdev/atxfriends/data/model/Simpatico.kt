package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.SimpaticoFields
import java.time.Instant

/**
 * `simpaticoAnswers/{uid}` — the v2 compatibility answers, decoded the way
 * SimpaticoService.swift does. A missing doc is simply an empty state.
 */
data class SimpaticoState(
    val userID: String,
    /** Keyed by question ID; a skipped question is absent. */
    val answers: Map<String, SimpaticoAnswer>,
    val completedAt: Instant?,
    /** True when the legacy pre-v2 `answers` field has data (drives the upgrade banner). */
    val hasLegacyAnswers: Boolean,
) {
    companion object {
        fun fromFirestore(userID: String, data: Map<String, Any?>?): SimpaticoState {
            val r = DocReader(data.orEmpty())
            val answers = r.map(SimpaticoFields.V2_ANSWERS).orEmpty()
                .mapNotNull { (questionID, raw) ->
                    @Suppress("UNCHECKED_CAST")
                    val answer = (raw as? Map<String, Any?>)?.let(SimpaticoAnswer::fromFirestore)
                    answer?.let { questionID to it }
                }
                .toMap()
            return SimpaticoState(
                userID = userID,
                answers = answers,
                completedAt = r.instant(SimpaticoFields.V2_COMPLETED_AT),
                hasLegacyAnswers = r.map(SimpaticoFields.LEGACY_ANSWERS).orEmpty().isNotEmpty(),
            )
        }
    }
}

data class SimpaticoAnswer(
    /** The option ID the user picked. */
    val answer: String,
    /** Option IDs the user is fine with a friend answering (always includes [answer]). */
    val acceptable: List<String>,
    /** Null when every option is acceptable ("Doesn't matter"). */
    val importance: SimpaticoImportance?,
) {
    companion object {
        /** An answer missing `answer` or `acceptable` is skipped, as on iOS. */
        fun fromFirestore(map: Map<String, Any?>): SimpaticoAnswer? {
            val r = DocReader(map)
            return SimpaticoAnswer(
                answer = r.string(SimpaticoFields.ANSWER) ?: return null,
                acceptable = r.stringList(SimpaticoFields.ACCEPTABLE) ?: return null,
                importance = r.string(SimpaticoFields.IMPORTANCE)?.let(SimpaticoImportance::fromRaw),
            )
        }
    }
}
