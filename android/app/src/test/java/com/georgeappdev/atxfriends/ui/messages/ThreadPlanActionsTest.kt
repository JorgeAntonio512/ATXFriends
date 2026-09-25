package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.TestDocs.ts
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import com.georgeappdev.atxfriends.domain.messages.RescheduleTime
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
import java.time.Duration
import java.time.ZoneId

/** The thread's plan actions: who may do what, double-tap guards, and visible failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadPlanActionsTest {

    private val now = at(day = 25, hour = 9)
    private val messages = FakeMessageStore()
    private val plans = FakePlanStore()
    private val calendar = FakeCalendarStore()
    private val showUps = FakeShowUpStore()

    /** Signed in as [me]; fixture plans are proposed by "me" to "sam". */
    private fun vm(me: String = "sam") =
        MessageThreadViewModel(MessageFixtures.thread("me_sam"), me, messages, plans, calendar, showUps, clock = { now })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun receiverAcceptsPendingProposal() {
        plans.plans.value = listOf(plan(status = PlanStatus.PENDING, confirmedDate = null))
        vm(me = "sam").acceptProposal("p1")
        val (id, update) = plans.updates.single()
        assertEquals("p1", id)
        assertEquals("confirmed", update.fields["status"])
    }

    @Test
    fun proposerCantAcceptOrDeclineTheirOwnProposal() {
        plans.plans.value = listOf(plan(status = PlanStatus.PENDING, confirmedDate = null))
        val vm = vm(me = "me")
        vm.acceptProposal("p1")
        vm.declineProposal("p1")
        assertTrue(plans.updates.isEmpty())
    }

    @Test
    fun doubleTap_writesOnce_andButtonsAreBusyMeanwhile() {
        plans.plans.value = listOf(plan(status = PlanStatus.PENDING, confirmedDate = null))
        plans.gate = CompletableDeferred()
        val vm = vm()
        vm.declineProposal("p1")
        assertTrue("p1" in vm.state.value.busyPlanIDs)
        vm.declineProposal("p1")
        vm.acceptProposal("p1")
        plans.gate!!.complete(Unit)
        assertEquals(1, plans.updates.size)
        assertEquals("declined", plans.updates.single().second.fields["status"])
        assertFalse("p1" in vm.state.value.busyPlanIDs)
    }

    @Test
    fun failedChange_showsWhichOneFailed_andFreesTheButtons() {
        plans.plans.value = listOf(plan(status = PlanStatus.PENDING, confirmedDate = null))
        plans.updateError = IOException("offline")
        val vm = vm()
        vm.acceptProposal("p1")
        assertEquals(PlanAction.ACCEPT, vm.state.value.planError)
        assertTrue(vm.state.value.busyPlanIDs.isEmpty())
        vm.dismissPlanError()
        assertNull(vm.state.value.planError)
    }

    @Test
    fun rescheduleAnswers_onlyForTheNonRequester() {
        plans.plans.value = listOf(plan(status = PlanStatus.COUNTER_PROPOSED, counterDate = at(day = 27), counterProposedBy = "sam"))
        vm(me = "sam").acceptReschedule("p1")
        assertTrue("the requester can't answer", plans.updates.isEmpty())

        vm(me = "me").declineReschedule("p1")
        assertEquals("confirmed", plans.updates.single().second.fields["status"])
        vm(me = "me").acceptReschedule("p1")
        assertEquals(ts(at(day = 27)), plans.updates.last().second.fields["confirmedDate"])
    }

    @Test
    fun cancel_asksFirst_thenCancels() {
        plans.plans.value = listOf(plan())
        val vm = vm()
        vm.askToCancel("p1", fromList = false)
        assertEquals("p1", vm.state.value.confirmingCancelPlanID)
        assertTrue(plans.updates.isEmpty())

        vm.dismissCancel()
        assertNull(vm.state.value.confirmingCancelPlanID)
        assertTrue("Keep does nothing", plans.updates.isEmpty())

        vm.askToCancel("p1", fromList = true)
        assertTrue(vm.state.value.cancelFromList)
        vm.confirmCancel()
        assertEquals(mapOf("status" to "cancelled", "updatedAt" to ts(now)), plans.updates.single().second.fields)
    }

    @Test
    fun cancelFailure_isShown() {
        plans.plans.value = listOf(plan())
        plans.updateError = IOException("denied")
        val vm = vm()
        vm.askToCancel("p1", fromList = false)
        vm.confirmCancel()
        assertEquals(PlanAction.CANCEL, vm.state.value.planError)
    }

    @Test
    fun reschedule_onlyForConfirmedPlans_startsAtTheCurrentTime_andClosesWhenSaved() {
        plans.plans.value = listOf(plan(confirmedDate = at(day = 26, hour = 19)), plan(id = "p2", status = PlanStatus.COUNTER_PROPOSED, counterDate = at(day = 27)))
        val vm = vm(me = "me")
        vm.openReschedule("p2")
        assertNull("hidden while a request is pending", vm.state.value.reschedulingPlanID)

        vm.openReschedule("p1")
        assertEquals("p1", vm.state.value.reschedulingPlanID)
        assertEquals(at(day = 26, hour = 19), vm.rescheduleInitialTime())

        vm.requestReschedule(at(day = 28, hour = 10))
        val fields = plans.updates.single().second.fields
        assertEquals("counter", fields["status"])
        assertEquals(listOf(ts(at(day = 28, hour = 10))), fields["counterProposedDates"])
        assertEquals("me", fields["counterProposedBy"])
        assertNull("sheet closes once saved", vm.state.value.reschedulingPlanID)
    }

    @Test
    fun rescheduleFailure_keepsTheSheetOpen_withAnError() {
        plans.plans.value = listOf(plan())
        plans.updateError = IOException("offline")
        val vm = vm()
        vm.openReschedule("p1")
        vm.requestReschedule(at(day = 28))
        assertEquals("p1", vm.state.value.reschedulingPlanID)
        assertTrue(vm.state.value.rescheduleFailed)
        assertFalse(vm.state.value.rescheduleSaving)
    }

    @Test
    fun reschedule_refusesAPastTime_andDoubleTaps() {
        plans.plans.value = listOf(plan())
        val vm = vm()
        vm.openReschedule("p1")
        vm.requestReschedule(now.minus(Duration.ofMinutes(1)))
        assertTrue(vm.state.value.rescheduleTimePassed)
        assertTrue(plans.updates.isEmpty())

        plans.gate = CompletableDeferred()
        vm.requestReschedule(at(day = 28))
        vm.requestReschedule(at(day = 29))
        vm.closeReschedule()
        assertEquals("can't close while saving", "p1", vm.state.value.reschedulingPlanID)
        plans.gate!!.complete(Unit)
        assertEquals(1, plans.updates.size)
    }

    @Test
    fun rescheduleInitialTime_isAnHourFromNow_whenThePlanTimeHasPassed() {
        val past = plan(confirmedDate = now.minus(Duration.ofHours(2)))
        assertEquals(now.plus(Duration.ofHours(1)), RescheduleTime.initial(past, now))
    }

    @Test
    fun pickerDayAndTime_combineInTheLocalZone() {
        val zone = ZoneId.of("America/Chicago")
        val day = RescheduleTime.dayUtcMillis(at(day = 28, hour = 23), zone)
        assertEquals(at(day = 28, hour = 15, minute = 30), RescheduleTime.combine(day, 15, 30, zone))
        assertFalse(RescheduleTime.isSelectableDay(RescheduleTime.dayUtcMillis(at(day = 24), zone), now, zone))
        assertTrue(RescheduleTime.isSelectableDay(RescheduleTime.dayUtcMillis(now, zone), now, zone))
    }

    @Test
    fun addedToCalendar_isRememberedPerPlan_onThisDevice() {
        calendar.added += "p2"
        plans.plans.value = listOf(plan(), plan(id = "p2"))
        val vm = vm()
        assertEquals(setOf("p2"), vm.state.value.addedToCalendar)

        vm.onReturnedFromCalendar("p1")
        assertEquals(setOf("p1", "p2"), vm.state.value.addedToCalendar)
        assertTrue("p1" in calendar.added)
        assertEquals("a new thread for the same plans still knows", setOf("p1", "p2"), vm().state.value.addedToCalendar)
    }

    @Test
    fun calendarEvent_comesFromThePlanAndTheOtherPerson() {
        plans.plans.value = listOf(plan(activity = "Poker", confirmedDate = at(day = 26, hour = 19)))
        val event = vm().calendarEvent(plans.plans.value.single())
        assertEquals("Poker", event.title)
        assertEquals("Hanging out with Sam", event.notes)
        assertEquals(at(day = 26, hour = 21), event.end)
    }
}
