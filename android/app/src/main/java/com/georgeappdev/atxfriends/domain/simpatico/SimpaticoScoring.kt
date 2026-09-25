package com.georgeappdev.atxfriends.domain.simpatico

import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import kotlin.math.sqrt

/**
 * The 12 fixed questions' IDs and option IDs, in flow order (iOS `SimpaticoQuestionBank`),
 * taken from the generated [SimpaticoQuestions] bank.
 */
object SimpaticoQuestionBank {
    val optionIDs: Map<String, List<String>> =
        SimpaticoQuestions.all.associate { q -> q.id to q.options.map { it.id } }

    fun optionCount(questionID: String): Int? = optionIDs[questionID]?.size
}

/** Port of iOS `SimpaticoV2State.score(between:and:)` (spec §5.2). */
object SimpaticoScoring {
    const val MINIMUM_SHARED_QUESTIONS = 5

    /** 0–100, or null when the two share fewer than 5 answered questions (badge hidden). */
    fun score(a: SimpaticoState, b: SimpaticoState): Int? = score(a.answers, b.answers)

    fun score(a: Map<String, SimpaticoAnswer>, b: Map<String, SimpaticoAnswer>): Int? {
        // Counted before questions unknown to the bank are skipped — same as iOS.
        val sharedIDs = a.keys intersect b.keys
        if (sharedIDs.size < MINIMUM_SHARED_QUESTIONS) return null

        val combined = sqrt(satisfaction(a, b, sharedIDs) * satisfaction(b, a, sharedIDs))
        // Swift's .rounded() rounds halves away from zero (62.5 → 63). Math.round matches for
        // non-negative values; kotlin.math.round would round halves to even and disagree.
        return Math.round(combined * 100).toInt()
    }

    /** `answer`'s weight: 0 when every option is acceptable ("doesn't matter"). */
    fun weight(answer: SimpaticoAnswer, totalOptions: Int): Int {
        if (answer.acceptable.size >= totalOptions) return 0
        val importance = answer.importance ?: return 0
        return if (importance == SimpaticoImportance.UNKNOWN) 0 else importance.weight
    }

    /**
     * The fraction of `subject`'s weight where `other`'s answer is one `subject` accepts.
     * 1.0 when `subject` put zero weight on the shared questions.
     */
    private fun satisfaction(
        subject: Map<String, SimpaticoAnswer>,
        other: Map<String, SimpaticoAnswer>,
        sharedIDs: Set<String>,
    ): Double {
        var totalWeight = 0.0
        var satisfiedWeight = 0.0
        for (questionID in sharedIDs) {
            val mine = subject[questionID] ?: continue
            val theirs = other[questionID] ?: continue
            val totalOptions = SimpaticoQuestionBank.optionCount(questionID) ?: continue
            val weight = weight(mine, totalOptions).toDouble()
            totalWeight += weight
            if (theirs.answer in mine.acceptable) satisfiedWeight += weight
        }
        return if (totalWeight > 0) satisfiedWeight / totalWeight else 1.0
    }
}
