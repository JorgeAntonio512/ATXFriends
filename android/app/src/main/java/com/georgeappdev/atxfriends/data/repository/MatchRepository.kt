package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.domain.matching.DecisionWrite
import com.georgeappdev.atxfriends.domain.matching.MatchDecision
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Access to `matches`. Reads are one-time fetches, as on iOS. The only write is a Yay/Nay
 * decision; Android never creates match documents.
 */
class MatchRepository(private val db: FirebaseFirestore) {

    /**
     * Every match the user is in, as either user1 or user2 — the same two queries iOS runs
     * (rules only allow reading matches you're part of). Documents that don't decode are skipped.
     * Throws on network or permission failure.
     */
    suspend fun fetchMatches(uid: String): List<Match> = coroutineScope {
        val matches = db.collection(Collections.MATCHES)
        val asUser1 = async { matches.whereEqualTo(MatchFields.USER1_ID, uid).get().await() }
        val asUser2 = async { matches.whereEqualTo(MatchFields.USER2_ID, uid).get().await() }
        (asUser1.await().documents + asUser2.await().documents)
            .distinctBy { it.id }
            .mapNotNull { doc -> doc.data?.let { Match.fromFirestore(doc.id, it) } }
    }

    /**
     * Records the user's Yay (true) or Nay (false). Runs in a transaction that reads the latest
     * match first, so if both people say Yay at nearly the same moment, `isMutualMatch` still
     * comes out right. Writes only the fields in [MatchDecision.ALLOWED_FIELDS], field by field.
     * Throws on failure; nothing is written then.
     */
    suspend fun recordDecision(matchID: String, myID: String, yay: Boolean): DecisionWrite {
        val ref = db.collection(Collections.MATCHES).document(matchID)
        return db.runTransaction { tx ->
            val snapshot = tx.get(ref)
            val latest = snapshot.data?.let { Match.fromFirestore(snapshot.id, it) }
                ?: throw IllegalStateException("Match $matchID is missing or unreadable")
            val write = MatchDecision.write(latest, myID, yay, Instant.now())
            tx.update(ref, write.update.fields)
            write
        }.await()
    }
}
