package com.georgeappdev.atxfriends.ui.simpatico

import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.georgeappdev.atxfriends.data.repository.SimpaticoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SimpaticoViewModelTest {

    private class FakeStore(var state: SimpaticoState = SimpaticoState("u", emptyMap(), null, false)) : SimpaticoStore {
        val saved = mutableListOf<Pair<String, SimpaticoAnswer>>()
        var completedCalls = 0
        var fetchError: Exception? = null
        var saveError: Exception? = null
        var completeError: Exception? = null

        override suspend fun fetchState(uid: String): SimpaticoState = fetchError?.let { throw it } ?: state
        override suspend fun saveAnswer(uid: String, questionID: String, answer: SimpaticoAnswer) {
            saveError?.let { throw it }
            saved += questionID to answer
        }
        override suspend fun markComplete(uid: String, at: Instant) {
            completeError?.let { throw it }
            completedCalls++
        }
    }

    private class FakePositions : SimpaticoPositionStore {
        val map = mutableMapOf<String, Int>()
        override fun get(uid: String) = map[uid] ?: 0
        override fun save(uid: String, index: Int) { map[uid] = index }
        override fun clear(uid: String) { map.remove(uid) }
    }

    private val store = FakeStore()
    private val positions = FakePositions()
    private fun vm() = SimpaticoViewModel(store, positions, currentUid = { "u" })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newUser_seesBeforeYouBeginIntro_upgradeOnlyWithLegacyAndNoV2Answers() {
        val fresh = vm().state.value
        assertTrue(fresh.showIntro)
        assertFalse(fresh.showUpgradeBanner)

        store.state = SimpaticoState("u", emptyMap(), null, hasLegacyAnswers = true)
        assertTrue(vm().state.value.showUpgradeBanner)

        store.state = SimpaticoState("u", mapOf("humor" to SimpaticoAnswer("dry", listOf("dry"), SimpaticoImportance.VERY)), null, true)
        val resumed = vm().state.value
        assertFalse("with any v2 answer the flow resumes directly", resumed.showIntro)
        assertFalse(resumed.showUpgradeBanner)
    }

    @Test
    fun ownAnswer_isAlwaysAcceptable_andAllTicked_meansDoesntMatter() {
        val vm = vm().apply { start() }
        vm.selectAnswer("chill")
        assertEquals(setOf("chill"), vm.state.value.selectedAcceptable)
        assertFalse(vm.state.value.canAdvance)

        vm.selectImportance(SimpaticoImportance.SOMEWHAT)
        assertTrue(vm.state.value.canAdvance)

        listOf("outdoors", "explore", "goOut").forEach(vm::toggleAcceptable)
        assertTrue(vm.state.value.doesNotMatter)
        assertNull("importance clears when every option is ticked", vm.state.value.selectedImportance)
        assertTrue(vm.state.value.canAdvance)

        vm.saveAndAdvance()
        val (questionID, answer) = store.saved.single()
        assertEquals("saturday", questionID)
        assertEquals(SimpaticoAnswer("chill", listOf("outdoors", "explore", "chill", "goOut"), null), answer)
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals(1, positions.get("u"))
    }

    @Test
    fun untickingOwnAnswer_stillSavesIt_asAcceptable() {
        val vm = vm().apply { start() }
        vm.selectAnswer("chill")
        vm.toggleAcceptable("chill")
        vm.toggleAcceptable("outdoors")
        vm.selectImportance(SimpaticoImportance.VERY)
        vm.saveAndAdvance()
        assertEquals(listOf("outdoors", "chill"), store.saved.single().second.acceptable)
        assertEquals(SimpaticoImportance.VERY, store.saved.single().second.importance)
    }

    @Test
    fun skip_writesNothing_butRemembersThePosition() {
        val vm = vm().apply { start() }
        vm.skip()
        assertTrue(store.saved.isEmpty())
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals(1, positions.get("u"))
    }

    @Test
    fun reopening_resumesAtTheSavedQuestion_withItsAnswerPrefilled() {
        positions.save("u", 3)
        store.state = SimpaticoState(
            "u",
            mapOf("bestTime" to SimpaticoAnswer("evening", listOf("evening", "lateNight"), SimpaticoImportance.LITTLE)),
            null,
            false,
        )
        val s = vm().state.value
        assertEquals(3, s.currentIndex)
        assertEquals("evening", s.selectedAnswerID)
        assertEquals(setOf("evening", "lateNight"), s.selectedAcceptable)
        assertEquals(SimpaticoImportance.LITTLE, s.selectedImportance)
    }

    @Test
    fun saveFailure_showsError_andKeepsEverything() {
        store.saveError = IOException("offline")
        val vm = vm().apply { start() }
        vm.selectAnswer("goOut")
        vm.selectImportance(SimpaticoImportance.VERY)
        vm.saveAndAdvance()

        val s = vm.state.value
        assertEquals(SimpaticoError.SAVE, s.error)
        assertEquals(0, s.currentIndex)
        assertFalse(s.isSaving)
        assertEquals("goOut", s.selectedAnswerID)
        assertEquals(SimpaticoImportance.VERY, s.selectedImportance)
        assertNotNull("the answer is kept locally, like iOS", s.answers["saturday"])

        store.saveError = null
        vm.saveAndAdvance()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.currentIndex)
    }

    @Test
    fun finishingTheLastQuestion_marksComplete_andClearsThePosition() {
        positions.save("u", 11)
        val vm = vm().apply { start() }
        assertTrue(vm.state.value.isLastQuestion)
        vm.selectAnswer("flexible")
        vm.selectImportance(SimpaticoImportance.LITTLE)
        vm.saveAndAdvance()

        assertEquals(1, store.completedCalls)
        assertTrue(vm.state.value.isComplete)
        assertEquals(1, vm.state.value.answeredCount)
        assertEquals(0, positions.get("u"))
        assertFalse(positions.map.containsKey("u"))
    }

    @Test
    fun skippingTheLastQuestion_alsoMarksComplete() {
        positions.save("u", 11)
        val vm = vm().apply { start() }
        vm.skip()
        assertEquals(1, store.completedCalls)
        assertTrue(vm.state.value.isComplete)
    }

    @Test
    fun finishFailure_staysOnTheLastQuestion_withTheFinishError() {
        positions.save("u", 11)
        store.completeError = IOException("offline")
        val vm = vm().apply { start() }
        vm.skip()
        assertEquals(SimpaticoError.FINISH, vm.state.value.error)
        assertFalse(vm.state.value.isComplete)
        assertEquals(11, positions.get("u"))
    }

    @Test
    fun editAnswers_restartsAtQuestionOne_withSavedAnswersPrefilled() {
        store.state = SimpaticoState(
            "u",
            mapOf("saturday" to SimpaticoAnswer("chill", listOf("chill"), SimpaticoImportance.VERY)),
            Instant.EPOCH,
            false,
        )
        val vm = vm()
        assertTrue(vm.state.value.isComplete)
        vm.startEditing()
        val s = vm.state.value
        assertFalse(s.isComplete)
        assertEquals(0, s.currentIndex)
        assertEquals("chill", s.selectedAnswerID)
    }

    @Test
    fun goBack_movesAndRemembersThePosition() {
        positions.save("u", 2)
        val vm = vm().apply { start() }
        vm.goBack()
        assertEquals(1, vm.state.value.currentIndex)
        assertEquals(1, positions.get("u"))
    }

    @Test
    fun loadFailure_showsTheLoadError() {
        store.fetchError = IOException("offline")
        val s = vm().state.value
        assertFalse(s.isLoading)
        assertEquals(SimpaticoError.LOAD, s.error)
    }
}
