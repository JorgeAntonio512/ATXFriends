package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.MessageFields
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.addDocument
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.MessageKind
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** A match's messages as the live listener sees them. */
data class LiveMessages(
    /** Oldest first. */
    val messages: List<Message>,
    /** Messages this device sent that the server hasn't accepted yet ("Sending…"). */
    val pendingIDs: Set<String> = emptySet(),
)

/** What an open thread needs from `messages` (a seam for tests). */
interface MessageStore {
    fun messagesFor(matchID: String): Flow<LiveMessages>
    suspend fun send(message: NewDocument): String
    suspend fun markRead(messageID: String)
}

/**
 * Access to `messages` (MessagingService.swift / UnreadState.swift). Always queried by a known
 * matchID or receiverID, never unscoped. The only writes are new messages and the receiver's
 * `isRead → true` flip, both shaped by [MessageWrites].
 */
class MessageRepository(private val db: FirebaseFirestore) : MessageStore {

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
     * `listenToMessages`). Like iOS there's no limit: the full history comes down. A message
     * sent from this device shows up at once (Firestore's local write), marked pending until the
     * server accepts it, and disappears again if the server rejects it. The flow fails with the
     * Firestore error if the listener errors.
     */
    override fun messagesFor(matchID: String): Flow<LiveMessages> = callbackFlow {
        val registration = messages.whereEqualTo(MessageFields.MATCH_ID, matchID)
            .orderBy(MessageFields.SENT_AT, Query.Direction.ASCENDING)
            // Also re-emit when only a message's pending state changes (server accepted it).
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { s ->
                    trySend(
                        LiveMessages(
                            messages = s.documents.mapNotNull(::decode),
                            pendingIDs = s.documents.filter { it.metadata.hasPendingWrites() }.mapTo(mutableSetOf()) { it.id },
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /** Creates a message (iOS `sendMessage`); returns once the server accepts it. Throws on failure. */
    override suspend fun send(message: NewDocument): String = messages.addDocument(message)

    /** The receiver's read receipt on one message (iOS `markAsRead`). Throws on failure. */
    override suspend fun markRead(messageID: String) {
        messages.document(messageID).applyUpdate(MessageWrites.markRead())
    }

    /**
     * iOS `markAllAsRead` / UnreadState.markConversationRead: every unread message in [matchID]
     * addressed to [uid], flipped to read one by one. Throws on the first failure.
     */
    suspend fun markConversationRead(matchID: String, uid: String) {
        val unread = messages.whereEqualTo(MessageFields.MATCH_ID, matchID)
            .whereEqualTo(MessageFields.RECEIVER_ID, uid)
            .whereEqualTo(MessageFields.IS_READ, false)
            .get().await()
        for (doc in unread.documents) {
            doc.reference.applyUpdate(MessageWrites.markRead())
        }
    }

    /**
     * Match IDs that have unread messages for [uid], live — the same query iOS UnreadState uses
     * to decide which conversation rows show as unread and whether the Messages tab shows its dot.
     */
    fun unreadMatchIDs(uid: String): Flow<Set<String>> = callbackFlow {
        val registration = messages.whereEqualTo(MessageFields.RECEIVER_ID, uid)
            .whereEqualTo(MessageFields.IS_READ, false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { s -> trySend(UnreadMessages.matchIDs(s.documents.map { it.data.orEmpty() })) }
            }
        awaitClose { registration.remove() }
    }

    private fun decode(doc: DocumentSnapshot): Message? = doc.data?.let { Message.fromFirestore(doc.id, it) }
}

/** The exact documents and fields MessagingService.swift writes. */
object MessageWrites {

    /**
     * iOS `messageToFirestoreData` for a new message: senderID, receiverID, text, sentAt (the
     * device clock, as a Timestamp) and isRead = false always; matchID when non-empty; `kind`
     * only for non-text messages, so plain messages look exactly like legacy ones; planID only
     * when there is one. [text] must already be trimmed and within the rules' 1–5000 characters.
     */
    fun newMessage(
        matchID: String,
        senderID: String,
        receiverID: String,
        text: String,
        sentAt: Instant,
        kind: MessageKind = MessageKind.TEXT,
        planID: String? = null,
    ): NewDocument {
        require(text.isNotEmpty()) { "Firestore rules reject empty messages" }
        return NewDocument.Builder()
            .put(MessageFields.SENDER_ID, senderID)
            .put(MessageFields.RECEIVER_ID, receiverID)
            .put(MessageFields.TEXT, text)
            .put(MessageFields.SENT_AT, sentAt)
            .put(MessageFields.IS_READ, false)
            .apply {
                if (matchID.isNotEmpty()) put(MessageFields.MATCH_ID, matchID)
                if (kind != MessageKind.TEXT) put(MessageFields.KIND, kind)
                planID?.let { put(MessageFields.PLAN_ID, it) }
            }
            .build()
    }

    /** The only change rules allow on a message: the receiver setting `isRead` to true. */
    fun markRead(): DocumentUpdate = DocumentUpdate.Builder().put(MessageFields.IS_READ, true).build()
}

/** iOS UnreadState's reading of the unread-messages query. */
object UnreadMessages {
    /** Match IDs among the unread docs. Docs without a matchID (dead event DMs) don't count. */
    fun matchIDs(unreadDocs: List<Map<String, Any?>>): Set<String> =
        unreadDocs.mapNotNullTo(mutableSetOf()) { it[MessageFields.MATCH_ID] as? String }

    /** The Messages tab shows its dot when any conversation has an unread message (`hasUnreadMessages`). */
    fun showsTabDot(unreadMatchIDs: Set<String>): Boolean = unreadMatchIDs.isNotEmpty()
}
