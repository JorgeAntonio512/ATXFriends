package com.georgeappdev.atxfriends.ui.matches

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.matching.MatchDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class MatchesViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val me = UserProfile.fromFirestore("me", TestDocs.user())!!.copy(
        id = "me",
        activities = listOf(ProfileActivity("a1", "Hiking", true)),
        daySlotCombos = listOf(DaySlotCombo("Monday_Night")),
        blockedUsers = emptyList(),
        latitude = 30.27,
        longitude = -97.74,
    )
    private val sam = me.copy(id = "sam", displayName = "Sam", showUpThumbsUp = 4, showUpTotal = 5, locationSharingMode = LocationSharingMode.ONCE, latitude = 30.40, longitude = -97.60)
    private val kim = me.copy(id = "kim", displayName = "Kim", showUpTotal = 0, locationSharingMode = LocationSharingMode.OFF)

    private val pendingWithSam = Match.fromFirestore("me_sam", TestDocs.match())!!.copy(
        id = "me_sam", user1ID = "me", user2ID = "sam", user1Decision = null, user2Decision = true, isMutualMatch = false,
        overlappingActivityNames = emptyList(), overlappingCategoryNames = listOf("Outdoor & Nature"),
        overlappingDaySlots = listOf("Monday Night"),
    )
    private val connectedWithKim = Match.fromFirestore("kim_me", TestDocs.match())!!.copy(
        id = "kim_me", user1ID = "kim", user2ID = "me", user1Decision = true, user2Decision = true, isMutualMatch = true,
        overlappingActivityNames = listOf("Hiking", "Tacos", "Yoga"),
    )

    private val answers = listOf("saturday", "hangSize", "planning", "bestTime", "socialBattery")
        .associateWith { SimpaticoAnswer("x", listOf("x"), SimpaticoImportance.VERY) }

    private var matchesError: Exception? = null
    private val userFetches = mutableListOf<String>()

    /** A tiny in-memory "server" for the matches collection. */
    private val server = linkedMapOf<String, Match>()
    private val decisionCalls = mutableListOf<Triple<String, String, Boolean>>()
    private var decisionError: Exception? = null
    private var decisionGate: CompletableDeferred<Unit>? = null

    private fun viewModel(profiles: Map<String, UserProfile> = mapOf("me" to me, "sam" to sam, "kim" to kim)): MatchesViewModel {
        if (server.isEmpty()) listOf(pendingWithSam, connectedWithKim).forEach { server[it.id] = it }
        return MatchesViewModel(
            myID = "me",
            fetchUser = { id -> userFetches += id; profiles[id]?.let(UserDoc::Found) ?: UserDoc.Missing },
            fetchMatches = { matchesError?.let { throw it }; server.values.toList() },
            fetchSimpatico = { id -> SimpaticoState(id, answers, null, false) },
            recordDecision = { matchID, myID, yay ->
                decisionCalls += Triple(matchID, myID, yay)
                decisionGate?.await()
                decisionError?.let { throw it }
                val latest = server.getValue(matchID)
                val write = MatchDecision.write(latest, myID, yay, Instant.EPOCH)
                server[matchID] = if (latest.user1ID == myID) latest.copy(user1Decision = yay, isMutualMatch = write.isMutualAfter)
                else latest.copy(user2Decision = yay, isMutualMatch = write.isMutualAfter)
                write
            },
            celebrationDurationMillis = 6_000,
        )
    }

    @Test
    fun loadsSectionsAndDisplayValues() {
        val s = viewModel().state.value
        assertFalse(s.isLoading)
        assertFalse(s.loadFailed)

        val pending = s.pending.single()
        assertEquals("Sam", pending.name)
        assertEquals("Outdoor & Nature", pending.sharedCategory)
        assertEquals(listOf("Monday Night"), pending.sharedTimes)
        assertEquals("12.26 mi (iOS: 19,726.8 m) → ~15", "~15 mi away", pending.distanceText)
        assertTrue(pending.isPending)

        val connected = s.connected.single()
        assertEquals("Kim", connected.name)
        assertEquals("New", connected.showUpMeter)
        assertEquals(100, connected.simpaticoScore)
        assertNull("Kim has Share My Location off", connected.distanceText)
        assertTrue(connected.isMutual)
        assertFalse(connected.isPending)
    }

    @Test
    fun loadFailure_isVisible_andRefreshRecovers() {
        matchesError = IOException("offline")
        val vm = viewModel()
        assertTrue(vm.state.value.loadFailed)
        assertTrue(vm.state.value.isEmpty)

        matchesError = null
        vm.refresh()
        assertFalse(vm.state.value.loadFailed)
        assertFalse(vm.state.value.isRefreshing)
        assertEquals(1, vm.state.value.pending.size)
    }

    @Test
    fun reappearing_onlyRefetchesProfiles() {
        val vm = viewModel()
        vm.onAppear() // first appearance = the initial load
        userFetches.clear()
        vm.onAppear()
        assertEquals(setOf("sam", "kim"), userFetches.toSet())
    }

    @Test
    fun nay_recordsTheDecision_andTheCardLeavesPending() {
        val vm = viewModel()
        vm.decide("me_sam", yay = false)
        assertEquals(listOf(Triple("me_sam", "me", false)), decisionCalls)
        assertEquals(false, server.getValue("me_sam").user1Decision)
        assertTrue("match still exists — Nay never deletes", server.containsKey("me_sam"))
        assertTrue(vm.state.value.pending.isEmpty())
        assertNull(vm.state.value.celebration)
        assertFalse(vm.state.value.isDeciding)
    }

    @Test
    fun yayCompletingAMutualMatch_movesToConnected_andCelebrates() = runTest(UnconfinedTestDispatcher()) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.select("me_sam")
        vm.decide("me_sam", yay = true) // Sam already said Yay

        val s = vm.state.value
        assertTrue(s.pending.isEmpty())
        assertEquals(listOf("me_sam", "kim_me"), s.connected.map { it.matchID })
        assertEquals("Sam", s.celebration?.name)
        assertNull("detail sheet closes after deciding, like iOS", s.selectedMatchID)

        advanceTimeBy(5_999)
        assertEquals("Sam", vm.state.value.celebration?.name)
        advanceTimeBy(2)
        assertNull("auto-dismissed after 6 seconds", vm.state.value.celebration)
    }

    @Test
    fun yayWithoutTheOtherPerson_noCelebration() {
        val vm = viewModel()
        server["me_sam"] = pendingWithSam.copy(user2Decision = null)
        vm.decide("me_sam", yay = true)
        assertTrue(vm.state.value.pending.isEmpty())
        assertEquals(listOf("kim_me"), vm.state.value.connected.map { it.matchID })
        assertNull(vm.state.value.celebration)
    }

    @Test
    fun openAndMaybeLater() {
        val vm = viewModel()
        vm.decide("me_sam", yay = true)
        vm.openCelebration()
        assertNull(vm.state.value.celebration)
        assertEquals("Sam", vm.state.value.selected?.name)

        vm.dismissDetail()
        server["kim_me"] = connectedWithKim
        vm.dismissCelebration()
        assertNull(vm.state.value.celebration)
    }

    @Test
    fun doubleTapsAreIgnoredWhileSaving() {
        decisionGate = CompletableDeferred()
        val vm = viewModel()
        vm.decide("me_sam", yay = true)
        vm.decide("me_sam", yay = true)
        vm.decide("me_sam", yay = false)
        assertTrue(vm.state.value.isDeciding)
        decisionGate!!.complete(Unit)
        assertEquals(1, decisionCalls.size)
        assertFalse(vm.state.value.isDeciding)
    }

    @Test
    fun failedDecision_showsError_andLeavesTheCard() {
        decisionError = IOException("offline")
        val vm = viewModel()
        vm.decide("me_sam", yay = true)
        assertTrue(vm.state.value.decisionFailed)
        assertEquals(listOf("me_sam"), vm.state.value.pending.map { it.matchID })
        assertFalse(vm.state.value.isDeciding)
        vm.dismissDecisionError()
        assertFalse(vm.state.value.decisionFailed)
    }

    @Test
    fun selectingOpensTheDetail() {
        val vm = viewModel()
        vm.select("kim_me")
        assertEquals("Kim", vm.state.value.selected?.name)
        vm.dismissDetail()
        assertNull(vm.state.value.selected)
    }
}
