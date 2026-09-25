package com.georgeappdev.atxfriends.ui.plans

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.plans.DayChoice
import com.georgeappdev.atxfriends.domain.plans.PlaceSuggestion
import com.google.firebase.Timestamp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PlanComposerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Chicago")
    private var now = LocalDateTime.of(2026, 9, 25, 16, 20).atZone(zone).toInstant()
    private fun at(day: Int, hour: Int, minute: Int = 0): Instant = LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant()

    private val me = profile("me", "Me", listOf("Tacos", "Hiking"))
    private fun profile(id: String, name: String, activities: List<String>): UserProfile =
        UserProfile.fromFirestore(id, TestDocs.user())!!.copy(
            id = id, displayName = name, photoURLs = listOf("https://x/$id.jpg"),
            activities = activities.mapIndexed { i, a -> ProfileActivity("a$i", a, true) },
        )

    private val users = mutableMapOf(
        "me" to me,
        "bea" to profile("bea", "Bea", listOf("hiking", "Chess")),
        "cy" to profile("cy", "Cy", listOf("Tacos")),
    )
    private var matches = listOf(match("me", "bea", mutual = true), match("cy", "me", mutual = true), match("me", "dee", mutual = false))
    private fun match(u1: String, u2: String, mutual: Boolean) = Match.fromFirestore("${u1}_$u2", TestDocs.match())!!
        .copy(id = "${u1}_$u2", user1ID = u1, user2ID = u2, isMutualMatch = mutual)

    // Captured writes.
    private val proposed = mutableListOf<Pair<NewDocument, NewDocument>>()
    private val posted = mutableListOf<NewDocument>()
    private val invited = mutableListOf<NewDocument>()
    private var failWrites = false
    private var gate: CompletableDeferred<Unit>? = null

    private fun vm(request: ComposerRequest) = PlanComposerViewModel(
        request = request,
        myID = "me",
        fetchActivityNames = { listOf("Board Games", "Hiking", "Hot Pot", "Tacos", "Taco Crawl") },
        fetchUser = { id -> users[id]?.let(UserDoc::Found) ?: UserDoc.Missing },
        fetchMatches = { matches },
        propose = { plan, message ->
            gate?.await()
            if (failWrites) throw IOException("offline")
            proposed += plan to message("NEW-PLAN")
            "NEW-PLAN"
        },
        postTodayPlan = { doc ->
            if (failWrites) throw IOException("offline")
            posted += doc
            "NEW-TODAY"
        },
        createGroupPlan = { doc ->
            gate?.await()
            if (failWrites) throw IOException("offline")
            invited += doc
            "NEW-GROUP"
        },
        clock = { now },
        zone = { zone },
    )

    private val proposal = ComposerRequest(ComposerMode.Proposal("bea_me", "bea"))

    // ── Validation ────────────────────────────────────────────────────────────────────

    @Test
    fun freshSheet_cannotSubmit_andShowsNoRedHint() {
        val s = vm(proposal).state.value
        assertFalse(s.canSubmit)
        assertFalse(s.where.showMissingHint)
        assertNull(s.errorMessage)
    }

    @Test
    fun whereHint_onlyAfterFocusThenLeaveEmpty_andClearsOnTyping() {
        val vm = vm(proposal)
        vm.onWhereLeft()
        assertTrue(vm.state.value.where.showMissingHint)
        vm.onWhereChange("anything")
        assertFalse(vm.state.value.where.showMissingHint)
        vm.onWhereChange("   ")
        assertTrue("whitespace counts as empty", vm.state.value.where.showMissingHint)
    }

    @Test
    fun needsActivityAndPlace_whitespaceDoesNotCount() {
        val vm = vm(proposal)
        vm.onActivityChange("  ")
        vm.onWhereChange("Zilker")
        assertFalse(vm.state.value.canSubmit)
        vm.onActivityChange("Frisbee")
        assertTrue(vm.state.value.canSubmit)
        vm.onWhereCleared()
        assertFalse(vm.state.value.canSubmit)
    }

    @Test
    fun freeTypedText_isAValidLocation_andEditingDropsAPickedPlace() {
        val vm = vm(proposal)
        vm.onPlacePicked(PlaceSuggestion("1", "Zilker Park", "2100 Barton Springs Rd, Austin", 30.26, -97.77, 1.2))
        assertEquals(30.26, vm.state.value.where.latitude!!, 0.0)
        vm.onWhereChange("Zilker Park by the tree")
        with(vm.state.value.where) {
            assertNull(locationName)
            assertNull(latitude)
            assertNull(longitude)
        }
    }

    @Test
    fun groupInvite_alsoNeedsSomeoneChecked() {
        val vm = vm(ComposerRequest(ComposerMode.GroupInvite))
        vm.onActivityChange("Chess")
        vm.onWhereChange("Library")
        assertFalse(vm.state.value.canSubmit)
        vm.onToggleInvitee("bea")
        assertTrue(vm.state.value.canSubmit)
        vm.onToggleInvitee("bea")
        assertFalse(vm.state.value.canSubmit)
    }

    // ── Suggestions ───────────────────────────────────────────────────────────────────

    @Test
    fun suggestions_containsCaseInsensitive_fromTheSharedList() {
        val vm = vm(proposal)
        vm.onActivityChange("tAc")
        assertEquals(listOf("Tacos", "Taco Crawl"), vm.state.value.suggestions)
        vm.onSuggestionPicked("Taco Crawl")
        assertEquals("Taco Crawl", vm.state.value.activityName)
        assertTrue(vm.state.value.suggestions.isEmpty())
        vm.onActivityChange(" ")
        assertTrue(vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun openPostSuggestions_comeFromMyOwnProfileActivities() {
        val vm = vm(ComposerRequest(ComposerMode.OpenPost))
        vm.onActivityChange("o")
        assertEquals(listOf("Tacos"), vm.state.value.suggestions)
    }

    // ── Modes and prefill ─────────────────────────────────────────────────────────────

    @Test
    fun proposal_defaultsToNextHour_andAcceptsAThreadChip() {
        val s = vm(ComposerRequest(ComposerMode.Proposal("m", "bea"), initialActivity = "Chess")).state.value
        assertEquals(at(25, 17), s.date)
        assertEquals("Chess", s.activityName)
        assertFalse(s.isPrefilled)
    }

    @Test
    fun openPost_blank_defaultsToTodayNextHour() {
        val s = vm(ComposerRequest(ComposerMode.OpenPost)).state.value
        assertEquals(DayChoice.TODAY, s.dayChoice)
        assertEquals(at(25, 17), s.date)
    }

    @Test
    fun openPost_ghostCardPrefill_picksTomorrowForATomorrowSlot() {
        val s = vm(ComposerRequest(ComposerMode.OpenPost, ComposerPrefill("Hiking", at(26, 8)))).state.value
        assertEquals(DayChoice.TOMORROW, s.dayChoice)
        assertEquals(at(26, 8), s.date)
        assertEquals("Hiking", s.activityName)
        assertTrue(s.isPrefilled)
    }

    @Test
    fun openPost_switchingSegment_resetsToThatSegmentsDefault() {
        val vm = vm(ComposerRequest(ComposerMode.OpenPost))
        vm.onDayChoice(DayChoice.TOMORROW)
        assertEquals(at(26, 0), vm.state.value.date)
        vm.onPostTimePicked(LocalTime.of(23, 0))
        assertEquals("clamped to the 24h ceiling", at(26, 16, 20), vm.state.value.date)
    }

    @Test
    fun openPost_lateAtNight_todayIsDisabled() {
        now = at(25, 23, 50)
        val vm = vm(ComposerRequest(ComposerMode.OpenPost))
        assertEquals(DayChoice.TOMORROW, vm.state.value.dayChoice)
        assertFalse(vm.state.value.todayAvailable)
        vm.onDayChoice(DayChoice.TODAY)
        assertEquals(DayChoice.TOMORROW, vm.state.value.dayChoice)
    }

    @Test
    fun anyDate_pickingAPastTimeToday_snapsToNow() {
        val vm = vm(proposal)
        vm.onDatePicked(LocalDate.of(2026, 9, 30))
        assertEquals(at(30, 17), vm.state.value.date)
        vm.onTimePicked(LocalTime.of(9, 5))
        assertEquals(at(30, 9, 5), vm.state.value.date)
        vm.onDatePicked(LocalDate.of(2026, 9, 25))
        assertEquals(at(25, 16, 21), vm.state.value.date)
    }

    @Test
    fun groupInvite_listsOnlyMutualMatches_andPrechecksSharedActivity() {
        val vm = vm(ComposerRequest(ComposerMode.GroupInvite, ComposerPrefill("Hiking", at(27, 12))))
        val s = vm.state.value
        assertEquals(listOf("bea", "cy"), s.inviteeOptions.map { it.userID })
        assertEquals("case-insensitive match on the activity", setOf("bea"), s.selectedInviteeIDs)
        assertEquals("https://x/bea.jpg", s.inviteeOptions[0].photoURL)
        assertTrue(s.isPrefilled)
    }

    @Test
    fun groupInvite_failedMatchLoad_saysSo_andCanRetry() {
        val real = matches
        val vm = PlanComposerViewModel(
            ComposerRequest(ComposerMode.GroupInvite), "me", { emptyList() }, { id -> users[id]?.let(UserDoc::Found) ?: UserDoc.Missing },
            fetchMatches = { if (failWrites) real else throw IOException("offline") },
            propose = { _, _ -> "" }, postTodayPlan = { "" }, createGroupPlan = { "" }, clock = { now }, zone = { zone },
        )
        assertTrue(vm.state.value.inviteesLoadFailed)
        failWrites = true
        vm.retryInvitees()
        assertFalse(vm.state.value.inviteesLoadFailed)
        assertEquals(2, vm.state.value.inviteeOptions.size)
    }

    // ── Submit ────────────────────────────────────────────────────────────────────────

    @Test
    fun proposal_writesPlanAndMessage_thenFinishes() {
        val vm = vm(proposal)
        vm.onActivityChange("  Frisbee ")
        vm.onWhereChange(" Zilker Park ")
        vm.submit()

        val (plan, message) = proposed.single()
        assertEquals("Frisbee", (plan.fields["activity"] as Map<*, *>)["name"])
        assertEquals("Zilker Park", plan.fields["location"])
        assertEquals("bea_me", plan.fields["matchID"])
        assertEquals("bea", plan.fields["receiverID"])
        assertEquals(listOf(Timestamp(at(25, 17).epochSecond, 0)), plan.fields["proposedDates"])
        assertEquals("NEW-PLAN", message.fields["planID"])
        assertTrue((message.fields["text"] as String).startsWith("Proposed Frisbee · Sep 25, 2026"))
        assertEquals(ComposerResult.Proposed("bea_me", "NEW-PLAN"), vm.state.value.result)
    }

    @Test
    fun submit_blocksDoubleTaps_whileSaving() = runTest {
        gate = CompletableDeferred()
        val vm = vm(proposal)
        vm.onActivityChange("Frisbee")
        vm.onWhereChange("Zilker")
        vm.submit()
        assertTrue(vm.state.value.isSubmitting)
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        gate!!.complete(Unit)
        assertEquals(1, proposed.size)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun failure_showsIosMessage_keepsInput_andAllowsRetry() {
        failWrites = true
        val vm = vm(proposal)
        vm.onActivityChange("Frisbee")
        vm.onWhereChange("Zilker")
        vm.submit()
        with(vm.state.value) {
            assertEquals("Couldn't send proposal. Please try again.", errorMessage)
            assertEquals("Frisbee", activityName)
            assertEquals("Zilker", where.text)
            assertNull(result)
            assertTrue(canSubmit)
        }
        failWrites = false
        vm.submit()
        assertNull(vm.state.value.errorMessage)
        assertEquals(1, proposed.size)
    }

    @Test
    fun openPost_reclampsAtSubmit_andReturnsThePlanForTheFeed() {
        val vm = vm(ComposerRequest(ComposerMode.OpenPost))
        vm.onActivityChange("Tacos")
        vm.onWhereChange("Veracruz")
        vm.onPostTimePicked(LocalTime.of(16, 40))
        now = at(25, 16, 30) // sheet sat open: 4:40 is now under the 15-minute lead
        vm.submit()

        val doc = posted.single()
        assertEquals(Timestamp(at(25, 16, 45).epochSecond, 0), doc.fields["scheduledTime"])
        assertEquals("open", doc.fields["status"])
        val result = vm.state.value.result as ComposerResult.Posted
        assertEquals("NEW-TODAY", result.plan.id)
        assertEquals(at(25, 16, 45), result.plan.scheduledTime)
    }

    @Test
    fun openPost_failureMessage_includesTheCause() {
        failWrites = true
        val vm = vm(ComposerRequest(ComposerMode.OpenPost))
        vm.onActivityChange("Tacos")
        vm.onWhereChange("Veracruz")
        vm.submit()
        assertEquals("Could not post plan: offline", vm.state.value.errorMessage)
    }

    @Test
    fun groupInvite_writesCheckedInviteesInListOrder() {
        val vm = vm(ComposerRequest(ComposerMode.GroupInvite))
        vm.onActivityChange("Chess")
        vm.onWhereChange("Library")
        vm.onToggleInvitee("cy")
        vm.onToggleInvitee("bea")
        vm.submit()
        val doc = invited.single()
        assertEquals(listOf("bea", "cy"), doc.fields["inviteeIDs"])
        assertEquals("me", doc.fields["hostID"])
        assertEquals(ComposerResult.Invited("NEW-GROUP"), vm.state.value.result)
    }

    @Test
    fun groupInvite_failure_usesIosText() {
        failWrites = true
        val vm = vm(ComposerRequest(ComposerMode.GroupInvite))
        vm.onActivityChange("Chess")
        vm.onWhereChange("Library")
        vm.onToggleInvitee("bea")
        vm.submit()
        assertEquals("Couldn't send invites. Please try again.", vm.state.value.errorMessage)
        assertEquals(setOf("bea"), vm.state.value.selectedInviteeIDs)
    }

    // region No connection: a send that hangs says so after 10 seconds

    private fun readyInvite(): PlanComposerViewModel = vm(ComposerRequest(ComposerMode.GroupInvite)).apply {
        onActivityChange("Chess")
        onWhereChange("Library")
        onToggleInvitee("bea")
    }

    @Test
    fun hangingSend_saysWaitingForConnection_afterTenSeconds_thenFinishes() {
        gate = CompletableDeferred()
        val vm = readyInvite()
        vm.submit()
        dispatcher.scheduler.advanceTimeBy(SLOW_WRITE_AFTER_MS - 1)
        assertFalse(vm.state.value.waitingForConnection)
        dispatcher.scheduler.advanceTimeBy(2)
        assertTrue(vm.state.value.waitingForConnection)
        assertTrue("still sending, not failed", vm.state.value.isSubmitting)

        gate!!.complete(Unit) // back online: Firestore sends the queued write
        assertFalse(vm.state.value.waitingForConnection)
        assertEquals(ComposerResult.Invited("NEW-GROUP"), vm.state.value.result)
    }

    @Test
    fun quickSend_neverShowsTheOfflineMessage() {
        val vm = readyInvite()
        vm.submit()
        dispatcher.scheduler.advanceTimeBy(SLOW_WRITE_AFTER_MS * 2)
        assertFalse(vm.state.value.waitingForConnection)
        assertEquals(ComposerResult.Invited("NEW-GROUP"), vm.state.value.result)
    }

    @Test
    fun failedSend_afterWaiting_showsTheError_notTheOfflineMessage() {
        gate = CompletableDeferred()
        failWrites = true
        val vm = readyInvite()
        vm.submit()
        dispatcher.scheduler.advanceTimeBy(SLOW_WRITE_AFTER_MS + 1)
        gate!!.complete(Unit)
        assertFalse(vm.state.value.waitingForConnection)
        assertEquals("Couldn't send invites. Please try again.", vm.state.value.errorMessage)
    }

    // endregion
}
