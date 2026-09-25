package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.repository.LiveMessages
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MessageThreadViewModelTest {

    private val messages = FakeMessageStore()
    private val plans = FakePlanStore()
    private val calendar = FakeCalendarStore()
    private val showUps = FakeShowUpStore()
    private fun vm() = MessageThreadViewModel(MessageFixtures.thread("me_sam"), "me", messages, plans, calendar, showUps, clock = { TestDocs.T1 })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun send_writesTrimmedTextToTheOtherPerson_andClearsTheBox() {
        val vm = vm()
        vm.onDraftChange("  see you there!  ")
        vm.send()

        assertEquals("", vm.state.value.draft)
        val fields = messages.sent.single().fields
        assertEquals("see you there!", fields["text"])
        assertEquals("me", fields["senderID"])
        assertEquals("sam", fields["receiverID"])
        assertEquals("me_sam", fields["matchID"])
        assertEquals(TestDocs.ts(TestDocs.T1), fields["sentAt"])
        assertEquals(false, fields["isRead"])
        assertFalse(vm.state.value.sendFailed)
    }

    @Test
    fun blankDraft_sendsNothing() {
        val vm = vm()
        vm.onDraftChange("   ")
        assertFalse(vm.state.value.canSend)
        vm.send()
        assertTrue(messages.sent.isEmpty())
    }

    @Test
    fun doubleTap_sendsOnce_becauseTheBoxClearsBeforeTheWrite() {
        messages.gate = CompletableDeferred()
        val vm = vm()
        vm.onDraftChange("hi")
        vm.send()
        vm.send()
        messages.gate!!.complete(Unit)
        assertEquals(1, messages.sent.size)
    }

    @Test
    fun failedSend_putsTheTextBack_andShowsTheError() {
        messages.sendError = IOException("offline")
        val vm = vm()
        vm.onDraftChange("hi")
        vm.send()

        assertEquals("hi", vm.state.value.draft)
        assertTrue(vm.state.value.sendFailed)
        vm.dismissSendError()
        assertFalse(vm.state.value.sendFailed)
    }

    @Test
    fun failedSend_keepsWhatWasTypedWhileItWasSending() {
        messages.gate = CompletableDeferred()
        messages.sendError = IOException("offline")
        val vm = vm()
        vm.onDraftChange("first")
        vm.send()
        vm.onDraftChange("second")
        messages.gate!!.complete(Unit)
        assertEquals("first\nsecond", vm.state.value.draft)
    }

    @Test
    fun tooLong_isNotSent_andSaysSo() {
        val vm = vm()
        vm.onDraftChange("a".repeat(5001))
        vm.send()
        assertTrue(messages.sent.isEmpty())
        assertEquals(5001, vm.state.value.draftTooLong)
        assertEquals("a".repeat(5001), vm.state.value.draft)
        vm.onDraftChange("a".repeat(5000))
        assertNull(vm.state.value.draftTooLong)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageThreadReadReceiptTest {

    private val messages = FakeMessageStore()
    private val plans = FakePlanStore()
    private val calendar = FakeCalendarStore()
    private val showUps = FakeShowUpStore()
    private fun vm() = MessageThreadViewModel(MessageFixtures.thread("me_sam"), "me", messages, plans, calendar, showUps)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun openingTheThread_marksUnreadIncomingRead_andNotMyOwn() {
        messages.live.value = LiveMessages(
            listOf(MessageFixtures.message(id = "in", senderID = "sam"), MessageFixtures.message(id = "out", senderID = "me")),
        )
        vm()
        assertEquals(listOf("in"), messages.markedRead)
    }

    @Test
    fun newIncomingMessage_whileOpen_isMarkedReadToo() {
        vm()
        messages.live.value = LiveMessages(listOf(MessageFixtures.message(id = "new", senderID = "sam")))
        assertEquals(listOf("new"), messages.markedRead)
    }

    @Test
    fun failedReceipt_isRetriedOnTheNextUpdate() {
        messages.markReadError = IOException("offline")
        vm()
        val unread = MessageFixtures.message(id = "in", senderID = "sam")
        messages.live.value = LiveMessages(listOf(unread))
        assertTrue(messages.markedRead.isEmpty())

        messages.markReadError = null
        messages.live.value = LiveMessages(listOf(unread), pendingIDs = setOf("x"))
        assertEquals(listOf("in"), messages.markedRead)
    }
}
