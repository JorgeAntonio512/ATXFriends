package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.matching.showUpMeter
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures
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

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadProfileTest {

    private val thread = MessageFixtures.thread("me_sam").copy(otherUserPhotoURL = "https://a/0.jpg")
    private val sam: UserProfile = UserProfile.fromFirestore("sam", TestDocs.user())!!

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun basic_isAConnectedMatch_withTheThreadsSharedInterests() {
        val ui = ThreadProfile.basic(thread)
        assertEquals("Sam", ui.name)
        assertEquals(listOf("https://a/0.jpg"), ui.photoURLs)
        assertEquals(listOf("Hiking"), ui.sharedActivities)
        assertTrue(ui.isMutual)
        assertFalse("no Yay / Nay in a thread", ui.isPending)
    }

    @Test
    fun loader_fillsInPhotosMeterAndScore_andToleratesAMissingScore() = runTest {
        val loaded = ThreadProfileLoader("me", myProfile = null, fetchUser = { UserDoc.Found(sam) }, fetchSimpatico = { throw IOException() })
            .load(thread)!!
        assertEquals(sam.photoURLs, loaded.photoURLs)
        assertEquals(showUpMeter(4, 5), loaded.showUpMeter)
        assertNull(loaded.simpaticoScore)
        assertNull("no distance without my own profile", loaded.distanceText)

        val unreadable = ThreadProfileLoader("me", null, fetchUser = { UserDoc.Unreadable }, fetchSimpatico = { SimpaticoState("x", emptyMap(), null, false) })
        assertNull(unreadable.load(thread))
    }

    @Test
    fun openProfile_showsAtOnce_thenTheFullProfile() {
        val gate = CompletableDeferred<Unit>()
        val full = ThreadProfile.full(thread, null, sam, 87)
        val vm = MessageThreadViewModel(
            thread, "me", FakeMessageStore(), FakePlanStore(), FakeCalendarStore(), FakeShowUpStore(),
            loadProfile = { gate.await(); full },
        )
        vm.openProfile()
        assertEquals(ThreadProfile.basic(thread), vm.state.value.profile)
        gate.complete(Unit)
        assertEquals(87, vm.state.value.profile?.simpaticoScore)

        vm.closeProfile()
        assertNull(vm.state.value.profile)
        vm.openProfile()
        assertEquals("reopens with the loaded profile", full, vm.state.value.profile)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadComposerTest {
    private val thread = MessageFixtures.thread("me_sam")
    private fun vm() = MessageThreadViewModel(thread, "me", FakeMessageStore(), FakePlanStore(), FakeCalendarStore(), FakeShowUpStore())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun plusAndProposeAPlan_openTheComposerForThisMatch_blank() {
        val vm = vm()
        vm.openComposer()
        val request = vm.state.value.composer!!
        assertEquals(com.georgeappdev.atxfriends.ui.plans.ComposerMode.Proposal("me_sam", "sam"), request.mode)
        assertNull(request.initialActivity)
        vm.closeComposer()
        assertNull(vm.state.value.composer)
    }

    @Test
    fun sharedInterestChip_prefillsTheActivity_andEachOpeningIsFresh() {
        val vm = vm()
        vm.openComposer("Hiking")
        val first = vm.state.value.composer!!
        assertEquals("Hiking", first.initialActivity)
        vm.closeComposer()
        vm.openComposer("Hiking")
        assertTrue(first.id != vm.state.value.composer!!.id)
    }

    @Test
    fun matchDetailsProposeAPlan_closesTheProfile_thenOpensTheComposer() {
        val vm = vm()
        vm.openProfile()
        vm.proposeFromProfile()
        assertNull(vm.state.value.profile)
        assertEquals(com.georgeappdev.atxfriends.ui.plans.ComposerMode.Proposal("me_sam", "sam"), vm.state.value.composer?.mode)
    }
}
