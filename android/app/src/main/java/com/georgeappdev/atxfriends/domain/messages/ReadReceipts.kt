package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Message

/**
 * When iOS marks messages read: while a thread is open, every time the messages load or the
 * listener fires, each unread message addressed to the signed-in user is flipped to read
 * (MessagingViewModel.loadMessages → `markAllAsRead`). Only the receiver may do this — rules
 * reject anyone else — so the sender's own messages are never touched.
 */
object ReadReceipts {
    /** IDs to mark read now: unread, addressed to [myID], and not already being marked. */
    fun toMark(messages: List<Message>, myID: String, inFlight: Set<String>): List<String> =
        messages.filter { it.receiverID == myID && !it.isRead && it.id !in inFlight }.map { it.id }
}
