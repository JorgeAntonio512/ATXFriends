package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.MessageFields
import java.time.Instant

/** `messages/{messageID}` — flat top-level collection, filtered by `matchID`. */
data class Message(
    val id: String,
    /** Empty for (dead) event messages, as on iOS. */
    val matchID: String,
    val eventID: String?,
    val senderID: String,
    val receiverID: String,
    val text: String,
    val sentAt: Instant,
    val isRead: Boolean,
    val kind: MessageKind,
    /** Set only when [kind] is PLAN_PROPOSAL; points at `plans/{planID}`. */
    val planID: String?,
) {
    companion object {
        /** Same required fields as iOS `firestoreDataToMessage`; null (skipped) if any is missing. */
        fun fromFirestore(id: String, data: Map<String, Any?>): Message? {
            val r = DocReader(data)
            return Message(
                id = id,
                senderID = r.string(MessageFields.SENDER_ID) ?: return null,
                receiverID = r.string(MessageFields.RECEIVER_ID) ?: return null,
                text = r.string(MessageFields.TEXT) ?: return null,
                sentAt = r.instant(MessageFields.SENT_AT) ?: return null,
                isRead = r.bool(MessageFields.IS_READ) ?: return null,
                matchID = r.string(MessageFields.MATCH_ID) ?: "",
                eventID = r.string(MessageFields.EVENT_ID),
                kind = MessageKind.fromRaw(r.string(MessageFields.KIND)),
                planID = r.string(MessageFields.PLAN_ID),
            )
        }
    }
}
