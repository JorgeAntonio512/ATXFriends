package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.TestDocs.ts
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.data.repository.ShowUpWrites
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.awaitingReport
import com.georgeappdev.atxfriends.domain.messages.otherUserID
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
class ShowUpPromptTest {

    private val now = at(day = 25, hour = 21)

    private fun todayPlan(
        creator: String = "sam",
        claimer: String? = "me",
        status: TodayPlanStatus = TodayPlanStatus.CLAIMED,
        time: java.time.Instant = at(day = 25, hour = 18),
        creatorVerdict: Boolean? = null,
        claimerVerdict: Boolean? = null,
    ) = TodayPlan(
        id = "tp1", creatorID = creator,
        activity = Activity(id = "a", name = "Tacos", isUserAdded = false, createdAt = now, isPrimary = true),
        scheduledTime = time, note = null, location = null, locationName = null, locationLatitude = null, locationLongitude = null,
        status = status, claimerID = claimer, createdAt = now, updatedAt = now,
        creatorReportedClaimer = creatorVerdict, claimerReportedCreator = claimerVerdict,
    )

    @Test
    fun report_isExactlyTheFiveFieldsIosWrites() {
        val fields = ShowUpWrites.report("tp1", "me", "sam", didShowUp = true, now = now).fields
        assertEquals(
            mapOf("reporterID" to "me", "reportedUserID" to "sam", "planID" to "tp1", "didShowUp" to true, "createdAt" to ts(now)),
            fields,
        )
    }

    @Test
    fun awaitingReport_onlyAfterTheTime_onClaimedPlans_untilMySideReports() {
        assertTrue(todayPlan().awaitingReport("me", now))
        assertTrue("creator side too", todayPlan().awaitingReport("sam", now))
        assertFalse("not yet happened", todayPlan(time = at(day = 25, hour = 22)).awaitingReport("me", now))
        assertFalse("never claimed", todayPlan(status = TodayPlanStatus.OPEN, claimer = null).awaitingReport("sam", now))
        assertFalse("already reported", todayPlan(claimerVerdict = true).awaitingReport("me", now))
        assertTrue("the other side's report doesn't count for me", todayPlan(creatorVerdict = false).awaitingReport("me", now))
        assertFalse("strangers", todayPlan().awaitingReport("stranger", now))
    }

    @Test
    fun otherUserID_isTheOtherSide() {
        assertEquals("sam", todayPlan().otherUserID("me"))
        assertEquals("me", todayPlan().otherUserID("sam"))
        assertNull(todayPlan().otherUserID("stranger"))
    }

    // region ViewModel

    private val showUps = FakeShowUpStore(pending = todayPlan())
    private fun vm() = MessageThreadViewModel(
        MessageFixtures.thread("me_sam"), "me", FakeMessageStore(), FakePlanStore(), FakeCalendarStore(), showUps, clock = { now },
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cardShows_thenReportAboutTheOtherPerson_hidesIt() {
        val vm = vm()
        assertEquals("tp1", vm.state.value.pendingShowUp?.id)
        vm.submitShowUp(didShowUp = false)
        val fields = showUps.submitted.single().fields
        assertEquals("me", fields["reporterID"])
        assertEquals("sam", fields["reportedUserID"])
        assertEquals(false, fields["didShowUp"])
        assertNull(vm.state.value.pendingShowUp)
    }

    @Test
    fun failedReport_keepsTheCard_andSaysSo() {
        showUps.submitError = IOException("offline")
        val vm = vm()
        vm.submitShowUp(didShowUp = true)
        assertTrue(vm.state.value.showUpFailed)
        assertFalse(vm.state.value.showUpSaving)
        assertEquals("tp1", vm.state.value.pendingShowUp?.id)
    }

    @Test
    fun doubleTap_reportsOnce() {
        showUps.gate = CompletableDeferred()
        val vm = vm()
        vm.submitShowUp(true)
        vm.submitShowUp(false)
        vm.dismissShowUp()
        assertEquals("can't dismiss mid-save", "tp1", vm.state.value.pendingShowUp?.id)
        showUps.gate!!.complete(Unit)
        assertEquals(1, showUps.submitted.size)
    }

    @Test
    fun dismiss_hidesWithoutReporting() {
        val vm = vm()
        vm.dismissShowUp()
        assertNull(vm.state.value.pendingShowUp)
        assertTrue(showUps.submitted.isEmpty())
    }

    // endregion
}
