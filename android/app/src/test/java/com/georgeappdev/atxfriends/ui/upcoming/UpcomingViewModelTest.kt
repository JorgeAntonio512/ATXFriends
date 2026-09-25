package com.georgeappdev.atxfriends.ui.upcoming

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.GroupPlanStore
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.upcoming.DayDot
import com.georgeappdev.atxfriends.domain.upcoming.MyPlanStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Chicago")
    private val now = at(25, 16)
    private fun at(day: Int, hour: Int): Instant = LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant()

    private fun profile(id: String, name: String) = UserProfile.fromFirestore(id, TestDocs.user())!!.copy(
        id = id, displayName = name,
        activities = listOf(ProfileActivity("a", "Hiking", true)),
        daySlotCombos = listOf(DaySlotCombo("Saturday_Evening"), DaySlotCombo("Sunday_Evening")),
    )
    private val users = mapOf("me" to profile("me", "Me"), "host" to profile("host", "Hana"), "bea" to profile("bea", "Bea"))

    private fun plan(id: String, date: Instant, host: String, invitees: List<String>, responses: Map<String, GroupPlanResponse>? = null) = GroupPlan(
        id, host, invitees, responses ?: invitees.associateWith { GroupPlanResponse.INVITED },
        Activity("a", "Chess", false, Instant.EPOCH, true), "Library", null, null, null,
        date, GroupPlanStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH,
    )

    private val invitedToSunday = plan("sun", at(27, 18), host = "host", invitees = listOf("me", "bea"))
    private val hostingMonday = plan("mon", at(28, 19), host = "me", invitees = listOf("bea"))

    private inner class FakeStore : GroupPlanStore {
        val live = MutableSharedFlow<List<GroupPlan>>(replay = 1)
        var failListener = false
        var failWrites = false
        var gate: CompletableDeferred<Unit>? = null
        val responds = mutableListOf<Pair<String, Map<String, Any?>>>()
        val cancels = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun groupPlansFor(uid: String): Flow<List<GroupPlan>> =
            if (failListener) flow { throw IOException("permission denied") } else live

        override suspend fun respond(planID: String, update: DocumentUpdate) {
            gate?.await()
            if (failWrites) throw IOException("offline")
            responds += planID to update.fields
        }

        override suspend fun cancel(planID: String, update: DocumentUpdate) {
            if (failWrites) throw IOException("offline")
            cancels += planID to update.fields
        }
    }

    private val store = FakeStore()
    private fun vm(myID: String = "me") = UpcomingViewModel(
        myID = myID, store = store,
        fetchUser = { id -> users[id]?.let(UserDoc::Found) ?: UserDoc.Missing },
        clock = { now }, zone = { zone },
    )

    @Test
    fun loadingUntilTheFirstSnapshot_thenDaysWithPlansGhostsAndDots() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppear()
        assertTrue(vm.state.value.isLoading)

        store.live.emit(listOf(invitedToSunday, hostingMonday))
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertTrue(s.hasAnyContent)
        assertEquals(7, s.days.size)

        val sat = s.days.first { it.date == LocalDate.of(2026, 9, 26) }
        assertEquals(DayDot.SLOT, sat.dot)
        assertEquals("Hiking", sat.ghost!!.activityName)

        val sun = s.days.first { it.date == LocalDate.of(2026, 9, 27) }
        assertEquals(DayDot.PLAN, sun.dot)
        assertNull("a plan day never shows a ghost", sun.ghost)
        with(sun.plans.single()) {
            assertEquals("Hana", hostName)
            assertEquals(MyPlanStatus.INVITED, myStatus)
            assertEquals(0, goingCount)
            assertEquals("Library", location)
        }
        assertEquals(MyPlanStatus.HOSTING, s.days.first { it.date == LocalDate.of(2026, 9, 28) }.plans.single().myStatus)
        vm.onDisappear()
    }

    @Test
    fun nothingAtAll_isTheEmptyState() = runTest(dispatcher) {
        val vm = UpcomingViewModel("nobody", store, { UserDoc.Missing }, { now }, { zone })
        vm.onAppear()
        store.live.emit(emptyList())
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.hasAnyContent)
        vm.onDisappear()
    }

    @Test
    fun listenerFailure_showsTheErrorState_andRetryRecovers() = runTest(dispatcher) {
        store.failListener = true
        val vm = vm()
        vm.onAppear()
        assertTrue(vm.state.value.loadFailed)
        assertFalse(vm.state.value.isLoading)

        store.failListener = false
        vm.retry()
        store.live.emit(listOf(invitedToSunday))
        assertFalse(vm.state.value.loadFailed)
        assertTrue(vm.state.value.hasAnyContent)
        vm.onDisappear()
    }

    @Test
    fun respond_writesOnlyMyOwnKey_andTheCardUpdatesLive() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppear()
        store.live.emit(listOf(invitedToSunday))
        vm.openDetail("sun")
        assertTrue(vm.state.value.detail!!.isInvitee)

        vm.respond(GroupPlanResponse.GOING)
        val (planID, fields) = store.responds.single()
        assertEquals("sun", planID)
        assertEquals(setOf("responses.me", "updatedAt"), fields.keys)
        assertEquals("going", fields["responses.me"])
        assertEquals(Timestamp(now.epochSecond, 0), fields["updatedAt"])

        // The listener delivers the change (and Bea's answer from her iPhone).
        store.live.emit(listOf(invitedToSunday.copy(responses = mapOf("me" to GroupPlanResponse.GOING, "bea" to GroupPlanResponse.GOING))))
        assertEquals(GroupPlanResponse.GOING, vm.state.value.detail!!.myResponse)
        assertEquals(2, vm.state.value.days.first { it.date == LocalDate.of(2026, 9, 27) }.plans.single().goingCount)
        vm.onDisappear()
    }

    @Test
    fun respond_blocksDoubleTaps_andFailureShowsTheIosError() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppear()
        store.live.emit(listOf(invitedToSunday))
        vm.openDetail("sun")

        store.gate = CompletableDeferred()
        vm.respond(GroupPlanResponse.CANT_MAKE)
        assertTrue(vm.state.value.isResponding)
        vm.respond(GroupPlanResponse.GOING)
        store.gate!!.complete(Unit)
        assertEquals(1, store.responds.size)
        assertEquals("cantMake", store.responds.single().second["responses.me"])

        store.failWrites = true
        vm.respond(GroupPlanResponse.GOING)
        assertEquals(UpcomingError.RESPOND, vm.state.value.actionError)
        assertFalse(vm.state.value.isResponding)
        vm.dismissError()
        assertNull(vm.state.value.actionError)
        vm.onDisappear()
    }

    @Test
    fun hostCancel_writesCancelled_andClosesTheDetail() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppear()
        store.live.emit(listOf(hostingMonday))
        vm.openDetail("mon")
        assertTrue(vm.state.value.detail!!.isHost)

        vm.cancelPlan()
        assertEquals(listOf("mon" to mapOf("status" to "cancelled", "updatedAt" to Timestamp(now.epochSecond, 0))), store.cancels)
        assertNull(vm.state.value.detail)

        // The listener then drops it from the strip (cancelled plans never show).
        store.live.emit(listOf(hostingMonday.copy(status = GroupPlanStatus.CANCELLED)))
        assertTrue(vm.state.value.days.all { it.plans.isEmpty() })
        vm.onDisappear()
    }

    @Test
    fun cancelFailure_keepsTheDetailOpen_withTheIosError() = runTest(dispatcher) {
        store.failWrites = true
        val vm = vm()
        vm.onAppear()
        store.live.emit(listOf(hostingMonday))
        vm.openDetail("mon")
        vm.cancelPlan()
        assertEquals(UpcomingError.CANCEL, vm.state.value.actionError)
        assertEquals("mon", vm.state.value.detail!!.id)
        assertFalse(vm.state.value.isCancelling)
        vm.onDisappear()
    }

    @Test
    fun detail_showsACancelFromElsewhere() = runTest(dispatcher) {
        val vm = vm()
        vm.onAppear()
        store.live.emit(listOf(invitedToSunday))
        vm.openDetail("sun")
        store.live.emit(listOf(invitedToSunday.copy(status = GroupPlanStatus.CANCELLED)))
        assertTrue(vm.state.value.detail!!.isCancelled)
        vm.onDisappear()
    }

    @Test
    fun todayAndLaterSections_andTheSentInviteIsConfirmedAndLocated() = runTest(dispatcher) {
        val today = plan("today", at(25, 18), host = "me", invitees = listOf("bea"))
        val later = plan("later", now.plus(java.time.Duration.ofDays(10)), host = "me", invitees = listOf("bea"))
        val vm = vm()
        vm.onAppear()

        vm.onInviteSent("today")
        assertEquals("confirmed before the listener catches up", "today", vm.state.value.justSent?.planID)
        assertNull(vm.state.value.justSent?.plan)

        store.live.emit(listOf(today, hostingMonday, later))
        val s = vm.state.value
        assertEquals(listOf("today"), s.todayPlans.map { it.id })
        assertEquals(listOf("later"), s.laterPlans.map { it.id })
        assertEquals(SECTION_TODAY, s.justSent?.sectionKey)
        assertEquals(at(25, 18), s.justSent?.plan?.date)

        vm.onInviteSent("mon")
        assertEquals(daySectionKey(LocalDate.of(2026, 9, 28)), vm.state.value.justSent?.sectionKey)
        vm.onInviteSent("later")
        assertEquals(SECTION_LATER, vm.state.value.justSent?.sectionKey)

        vm.clearJustSent()
        assertNull(vm.state.value.justSent)
        vm.onDisappear()
    }
}
