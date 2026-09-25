package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.domain.messages.MessageDraft.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDraftTest {

    @Test
    fun trimsWhitespaceAndNewlines_likeIos() {
        assertEquals(Result.Ready("hey there"), MessageDraft.prepare("  \n hey there \n\t"))
    }

    @Test
    fun blankDraft_sendsNothing() {
        assertEquals(Result.Empty, MessageDraft.prepare(""))
        assertEquals(Result.Empty, MessageDraft.prepare(" \n\t "))
    }

    @Test
    fun fiveThousandCharacters_isTheLimit() {
        assertEquals(Result.Ready("a".repeat(5000)), MessageDraft.prepare("a".repeat(5000)))
        assertEquals(Result.TooLong(5001), MessageDraft.prepare("a".repeat(5001)))
    }

    @Test
    fun emoji_countAsOneCharacterEach() {
        val emoji = "😀".repeat(5000) // 10,000 UTF-16 units
        assertEquals(Result.Ready(emoji), MessageDraft.prepare(emoji))
    }

    @Test
    fun restore_putsFailedTextBack_keepingAnythingTypedSince() {
        assertEquals("first", MessageDraft.restore("first", ""))
        assertEquals("first", MessageDraft.restore("first", "  "))
        assertEquals("first\nsecond", MessageDraft.restore("first", "second"))
    }
}
