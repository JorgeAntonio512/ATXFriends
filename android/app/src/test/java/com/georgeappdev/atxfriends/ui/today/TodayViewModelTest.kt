package com.georgeappdev.atxfriends.ui.today

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.today.OpenSlotsHeader
import com.georgeappdev.atxfriends.domain.today.PlanAlreadyClaimedException
import com.georgeappdev.atxfriends.navigation.ThreadRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Chicago")
    /** Friday 2026-09-25 16:00 in Austin, plus virtual test time. */
    private val start = LocalDateTime.of(2026, 9, 25, 16, 0).atZone(zone).toInstant()

    private val me = UserProfile.fromFirestore("me", TestDocs.user())!!.copy(
        id = "me",
        activities = listOf(ProfileActivity("a1", "Tacos", true)),
        daySlotCombos = listOf(DaySlotCombo("Friday_Night")),
    )
    private val sam = me.copy(id = "sam", displayName = "Sam", showUpThumbsUp = 4, showUpTotal = 5)

    private fun plan(id: String, activity: String, minutesFromStart: Long, creator: String) = TodayPlan(
        id = id, creatorID = creator,
        activity = Activity("act-$activity", activity, false, Instant.EPOCH, true),
        scheduledTime = start.plusSeconds(minutesFromStart * 60), note = "bring cash", location = "Veracruz",
        locationName = null, locationLatitude = null, locationLongitude = null, status = TodayPlanStatus.OPEN,
        claimerID = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        creatorReportedClaimer = null, claimerReportedCreator = null,
    )

    private val mine = plan("mine", "Tacos", 180, creator = "me")
    private val samsTrivia = plan("sams", "Trivia", 120, creator = "sam")

    private val updates = MutableSharedFlow<List<TodayPlan>>()
    private var fetchResult: () -> List<TodayPlan> = { listOf(samsTrivia, mine) }

    private fun TestScope.viewModel() = TodayViewModel(
        myID = "me",
        fetchOpenPlans = { fetchResult() },
        openPlansUpdates = { updates },
        fetchUser = { id -> mapOf("me" to me, "sam" to sam)[id]?.let(UserDoc::Found) ?: UserDoc.Missing },
        clock = { start.plusMillis(testScheduler.currentTime) },
        zone = { zone },
        claimPlan = { plan, claimer -> claimCalls += plan.id to claimer; claimResult() },
    )

    private val claimCalls = mutableListOf<Pair<String, String>>()
    private var claimResult: suspend () -> String = { "me_sam" }

    @Test
    fun loadsFeedWithPosterInfoAndOwnPost() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf("sams", "mine"), state.plans.map { it.id })
        assertEquals("Sam", state.plans[0].posterName)
        assertEquals("80% (4/5)", state.plans[0].showUpMeter)
        assertFalse(state.plans[0].isOwnPlan)
        assertTrue(state.plans[1].isOwnPlan)
        assertEquals(listOf("Tacos", "Trivia"), state.availableActivities)
        assertEquals(OpenSlotsHeader.OR_POST_YOUR_OWN, state.openSlotsHeader)
        assertTrue(state.openSlots.isNotEmpty())
        vm.onDisappear()
    }

    @Test
    fun filterChipsToggle() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        vm.selectActivity("Trivia")
        assertEquals(listOf("sams"), vm.state.value.plans.map { it.id })
        vm.selectActivity("Trivia")
        assertNull(vm.state.value.activityFilter)
        vm.selectActivity("Tacos")
        vm.selectAll()
        assertEquals(2, vm.state.value.plans.size)
        vm.onDisappear()
    }

    @Test
    fun ownPlanVanishingFromListenerShowsBannerThenHides() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        updates.emit(listOf(samsTrivia))
        assertTrue(vm.state.value.isClaimedBannerVisible)
        assertEquals("Tacos", vm.state.value.claimedBannerActivity)
        assertEquals(listOf("sams"), vm.state.value.plans.map { it.id })

        advanceTimeBy(TodayViewModel.BANNER_MILLIS + 1)
        assertFalse(vm.state.value.isClaimedBannerVisible)
        vm.onDisappear()
    }

    @Test
    fun someoneElsesPlanVanishingShowsNoBanner() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        updates.emit(listOf(mine))
        assertFalse(vm.state.value.isClaimedBannerVisible)
        assertEquals(listOf("mine"), vm.state.value.plans.map { it.id })
        vm.onDisappear()
    }

    @Test
    fun newPlanFromListenerAppears() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        updates.emit(listOf(plan("new", "Bowling", 30, creator = "sam"), samsTrivia, mine))
        assertEquals(listOf("new", "sams", "mine"), vm.state.value.plans.map { it.id })
        vm.onDisappear()
    }

    @Test
    fun planLeavesFeedWhenItsStartTimePasses() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        advanceTimeBy(120 * 60_000L + 2)
        runCurrent()
        assertEquals(listOf("mine"), vm.state.value.plans.map { it.id })
        assertEquals(listOf("Tacos"), vm.state.value.availableActivities)
        vm.onDisappear()
    }

    @Test
    fun leavingTheScreenStopsListening() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        assertEquals(1, updates.subscriptionCount.value)
        vm.onDisappear()
        assertEquals(0, updates.subscriptionCount.value)
    }

    @Test
    fun loadFailureShowsErrorAndEmptyFeed() = runTest(dispatcher) {
        fetchResult = { throw IOException("offline") }
        val vm = viewModel()
        vm.onAppear()
        assertEquals("offline", vm.state.value.loadError)
        assertTrue(vm.state.value.plans.isEmpty())
        // Profile still loads, so the fallback/usual cards show with the unfiltered header.
        assertEquals(OpenSlotsHeader.YOUR_OPEN_SLOTS, vm.state.value.openSlotsHeader)
        vm.dismissError()
        assertNull(vm.state.value.loadError)
        vm.onDisappear()
    }

    // ── Posting and claiming ─────────────────────────────────────────────────────────

    @Test
    fun claim_opensTheThreadWithThePoster_andDropsTheCard() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        vm.claim("sams")
        assertEquals(listOf("sams" to "me"), claimCalls)
        assertEquals(ThreadRequest("me_sam", "sam", "Sam"), vm.state.value.threadToOpen)
        assertEquals(listOf("mine"), vm.state.value.plans.map { it.id })
        assertTrue(vm.state.value.claimingIDs.isEmpty())
        vm.onThreadOpened()
        assertNull(vm.state.value.threadToOpen)
        vm.onDisappear()
    }

    @Test
    fun claim_showsJoiningWhileSaving_andIgnoresDoubleTaps() = runTest(dispatcher) {
        val gate = CompletableDeferred<String>()
        claimResult = { gate.await() }
        val vm = viewModel()
        vm.onAppear()
        vm.claim("sams")
        assertEquals(setOf("sams"), vm.state.value.claimingIDs)
        vm.claim("sams")
        gate.complete("me_sam")
        assertEquals(1, claimCalls.size)
        assertTrue(vm.state.value.claimingIDs.isEmpty())
        vm.onDisappear()
    }

    @Test
    fun claim_lostRace_showsTheMessage_andResetsTheButton() = runTest(dispatcher) {
        claimResult = { throw PlanAlreadyClaimedException() }
        val vm = viewModel()
        vm.onAppear()
        vm.claim("sams")
        assertEquals("This plan is no longer available — someone got there first!", vm.state.value.actionError)
        assertTrue(vm.state.value.claimingIDs.isEmpty())
        assertNull(vm.state.value.threadToOpen)
        assertEquals("the card stays until the listener drops it", listOf("sams", "mine"), vm.state.value.plans.map { it.id })
        vm.dismissActionError()
        assertNull(vm.state.value.actionError)
        vm.onDisappear()
    }

    @Test
    fun claim_ownPostIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        vm.claim("mine")
        assertTrue(claimCalls.isEmpty())
        vm.onDisappear()
    }

    @Test
    fun onPosted_showsThePlanAtOnce_andFlashesPostedForTwoSeconds() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppear()
        vm.onPosted(plan("new", "Bowling", 30, creator = "me"))
        assertEquals(listOf("new", "sams", "mine"), vm.state.value.plans.map { it.id })
        assertTrue(vm.state.value.showPostedToast)
        advanceTimeBy(TodayViewModel.TOAST_MILLIS + 1)
        assertFalse(vm.state.value.showPostedToast)
        vm.onDisappear()
    }
}
