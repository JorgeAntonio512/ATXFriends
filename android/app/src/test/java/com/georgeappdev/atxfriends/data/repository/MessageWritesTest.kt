package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.MessageKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The message documents must be exactly what MessagingService.swift `messageToFirestoreData` writes. */
class MessageWritesTest {

    @Test
    fun textMessage_hasIosFieldsOnly_noKindNoPlanID_sentAtAsTimestamp_unread() {
        val doc = MessageWrites.newMessage("me_sam", "me", "sam", "Tacos tonight?", TestDocs.T1)
        assertEquals(
            mapOf(
                "senderID" to "me",
                "receiverID" to "sam",
                "text" to "Tacos tonight?",
                "sentAt" to TestDocs.ts(TestDocs.T1),
                "isRead" to false,
                "matchID" to "me_sam",
            ),
            doc.fields,
        )
    }

    @Test
    fun planProposal_addsKindAndPlanID_withIosRawValue() {
        val doc = MessageWrites.newMessage("me_sam", "me", "sam", "Proposed Poker · Sep 26", TestDocs.T1, MessageKind.PLAN_PROPOSAL, "plan-1")
        assertEquals("planProposal", doc.fields["kind"])
        assertEquals("plan-1", doc.fields["planID"])
    }

    @Test
    fun emptyMatchID_isLeftOut_likeIos() {
        val doc = MessageWrites.newMessage("", "me", "sam", "hi", TestDocs.T1)
        assertEquals(false, "matchID" in doc.fields)
    }

    @Test
    fun rulesRequiredKeys_areAllPresent() {
        val doc = MessageWrites.newMessage("me_sam", "me", "sam", "hi", TestDocs.T1)
        assertEquals(true, doc.fields.keys.containsAll(listOf("matchID", "senderID", "receiverID", "text", "sentAt", "isRead")))
    }

    @Test
    fun writtenMessage_decodesBackAsPlainText() {
        val doc = MessageWrites.newMessage("me_sam", "me", "sam", "hi", TestDocs.T1)
        val decoded = Message.fromFirestore("id", doc.fields)!!
        assertEquals(MessageKind.TEXT, decoded.kind)
        assertEquals(TestDocs.T1, decoded.sentAt)
        assertEquals(false, decoded.isRead)
        assertEquals("me_sam", decoded.matchID)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesEmptyText_whichRulesReject() {
        MessageWrites.newMessage("me_sam", "me", "sam", "", TestDocs.T1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesUnknownKind() {
        MessageWrites.newMessage("me_sam", "me", "sam", "hi", TestDocs.T1, MessageKind.UNKNOWN)
    }

    @Test
    fun readReceipt_isExactlyIsReadTrue_theOnlyChangeRulesAllow() {
        assertEquals(mapOf("isRead" to true), MessageWrites.markRead().fields)
    }
}
