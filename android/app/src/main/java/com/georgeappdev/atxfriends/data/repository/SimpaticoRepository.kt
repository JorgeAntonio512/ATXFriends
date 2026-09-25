package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.SimpaticoFields
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** What the Simpatico tab needs from `simpaticoAnswers` (a seam for tests). */
interface SimpaticoStore {
    suspend fun fetchState(uid: String): SimpaticoState
    suspend fun saveAnswer(uid: String, questionID: String, answer: SimpaticoAnswer)
    suspend fun markComplete(uid: String, at: Instant = Instant.now())
}

/**
 * Access to `simpaticoAnswers`. iOS fetches these one time, on demand. The only writes are to
 * the signed-in user's own doc, one question at a time (see [SimpaticoWrites]).
 */
class SimpaticoRepository(private val db: FirebaseFirestore) : SimpaticoStore {

    /** A missing doc is an empty state (the user hasn't started). Throws on network/permission failure. */
    override suspend fun fetchState(uid: String): SimpaticoState {
        val snapshot = db.collection(Collections.SIMPATICO_ANSWERS).document(uid).get().await()
        return SimpaticoState.fromFirestore(uid, snapshot.data)
    }

    /**
     * iOS `saveV2Answer`. Throws on failure — including when the doc doesn't exist yet, since
     * Android only ever changes existing docs (iOS creates it on the first answer).
     */
    override suspend fun saveAnswer(uid: String, questionID: String, answer: SimpaticoAnswer) {
        doc(uid).applyUpdate(SimpaticoWrites.answer(uid, questionID, answer))
    }

    /** iOS `markV2Complete`. */
    override suspend fun markComplete(uid: String, at: Instant) {
        doc(uid).applyUpdate(SimpaticoWrites.completed(at))
    }

    private fun doc(uid: String) = db.collection(Collections.SIMPATICO_ANSWERS).document(uid)
}

/**
 * The field-level updates SimpaticoService.swift makes. iOS writes
 * `{userID, v2Answers: {questionID: {answer, acceptable, importance?}}}` with mergeFields
 * `userID` and `v2Answers.{questionID}`; the dotted paths below change exactly those fields,
 * so every other question, `v2CompletedAt`, and the legacy `answers` field stay untouched.
 */
object SimpaticoWrites {

    fun answer(uid: String, questionID: String, answer: SimpaticoAnswer): DocumentUpdate {
        // Question IDs become part of a field path, so only plain identifiers are allowed.
        require(questionID.matches(Regex("[A-Za-z0-9]+"))) { "Bad question ID '$questionID'" }
        val path = "${SimpaticoFields.V2_ANSWERS}.$questionID"
        val importance = answer.importance
        return DocumentUpdate.Builder()
            .put(SimpaticoFields.USER_ID, uid)
            .put("$path.${SimpaticoFields.ANSWER}", answer.answer)
            .putStrings("$path.${SimpaticoFields.ACCEPTABLE}", answer.acceptable)
            .apply {
                // iOS replaces the whole answer map, so an importance from an earlier save must go.
                if (importance == null) delete("$path.${SimpaticoFields.IMPORTANCE}")
                else put("$path.${SimpaticoFields.IMPORTANCE}", importance)
            }
            .build()
    }

    /** `v2CompletedAt` as a Timestamp of the device's clock, as iOS writes it. */
    fun completed(at: Instant): DocumentUpdate =
        DocumentUpdate.Builder().put(SimpaticoFields.V2_COMPLETED_AT, at).build()
}
