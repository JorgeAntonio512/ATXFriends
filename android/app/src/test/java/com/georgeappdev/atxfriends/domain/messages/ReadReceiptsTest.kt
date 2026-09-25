package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.message
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadReceiptsTest {

    @Test
    fun marksOnlyUnreadMessagesAddressedToMe() {
        val mine = message(id = "mine", senderID = "me")          // to sam: never mine to mark
        val theirs = message(id = "theirs", senderID = "sam")     // to me, unread
        val read = message(id = "read", senderID = "sam").copy(isRead = true)
        assertEquals(listOf("theirs"), ReadReceipts.toMark(listOf(mine, theirs, read), "me", emptySet()))
    }

    @Test
    fun skipsReceiptsAlreadyBeingWritten() {
        val a = message(id = "a", senderID = "sam")
        val b = message(id = "b", senderID = "sam")
        assertEquals(listOf("b"), ReadReceipts.toMark(listOf(a, b), "me", setOf("a")))
    }
}
