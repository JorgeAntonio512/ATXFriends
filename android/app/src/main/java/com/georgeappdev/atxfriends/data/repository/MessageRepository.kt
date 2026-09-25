package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.MessageFields
import com.georgeappdev.atxfriends.data.model.Message
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Read-only access to `messages` (MessagingService.swift / UnreadState.swift). Always queried by
 * a known matchID or receiverID, never unscoped. Nothing here writes — not even `isRead`.
 */
class MessageRepository(private val db: FirebaseFirestore) {

    private val messages get() = db.collection(Collections.MESSAGES)

    /** The newest message in a match, or null (iOS `getLastMessage`). Throws on failure. */
    suspend fun fetchLastMessage(matchID: String): Message? =
        messages.whereEqualTo(MessageFields.MATCH_ID, matchID)
            .orderBy(MessageFields.SENT_AT, Query.Direction.DESCENDING)
            .limit(1)
            .get().await()
            .documents.firstNotNullOfOrNull(::decode)

    /**
     * Every message in a match, oldest first, re-emitted live on each change (iOS
     * `listenToMessages`). Like iOS there's no limit: the full history comes down. The flow
     * fails with the Firestore error if the listener errors.
     */
    fun messagesFor(matchID: String): Flow<List<Message>> = callbackFlow {
        val registration = messages.whereEqualTo(MessageFields.MATCH_ID, matchID)
            .orderBy(MessageFields.SENT_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { trySend(it.documents.mapNotNull(::decode)) }
            }
        awaitClose { registration.remove() }
    }

    /**
     * Match IDs that have unread messages for [uid], live — the same query iOS UnreadState uses
     * to decide which conversation rows show as unread.
     */
    fun unreadMatchIDs(uid: String): Flow<Set<String>> = callbackFlow {
        val registration = messages.whereEqualTo(MessageFields.RECEIVER_ID, uid)
            .whereEqualTo(MessageFields.IS_READ, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { s ->
                    trySend(s.documents.mapNotNull { it.data?.get(MessageFields.MATCH_ID) as? String }.toSet())
                }
            }
        awaitClose { registration.remove() }
    }

    private fun decode(doc: DocumentSnapshot): Message? = doc.data?.let { Message.fromFirestore(doc.id, it) }
}
