package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.repository.UserDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val session = TestSession()
    private val writer = FakeProfileWriter()
    private val edits = ProfileEdits(session.manager, writer) { FIXED_NOW }
    private var fetch: suspend (String) -> UserDoc = { UserDoc.Found(session.profile) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = NotificationsViewModel(edits) { fetch(it) }

    @Test
    fun loadsTheStoredPreferences() {
        // Sample doc: newMessages = false, groupUpdates = false.
        val s = vm().state.value
        assertFalse(s.isLoading)
        assertTrue(s.isOn(NotificationToggle.NEW_MATCHES))
        assertFalse(s.isOn(NotificationToggle.MESSAGES))
    }

    @Test
    fun eachToggle_writesAllFiveKeys_keepingTheHiddenGroupUpdatesValue() {
        val vm = vm()
        vm.setToggle(NotificationToggle.PLAN_REQUESTS, false)
        val f = writer.lastFields
        assertEquals(true, f["notificationPreferences.newMatches"])
        assertEquals(false, f["notificationPreferences.newMessages"])
        assertEquals(false, f["notificationPreferences.planRequests"])
        assertEquals(true, f["notificationPreferences.planConfirmations"])
        assertEquals("no toggle, value preserved", false, f["notificationPreferences.groupUpdates"])
        assertTrue("updatedAt" in f)
        assertFalse(session.profile.notificationPreferences.planRequests)
    }

    @Test
    fun rememberedAfterLeavingAndComingBack() {
        vm().setToggle(NotificationToggle.NEW_MATCHES, false)
        fetch = { UserDoc.Found(session.profile) }
        assertFalse(vm().state.value.isOn(NotificationToggle.NEW_MATCHES))
    }

    @Test
    fun failedSave_isVisible_keepsTheToggle_andRetries() {
        writer.failNext = true
        val vm = vm()
        vm.setToggle(NotificationToggle.PLAN_CONFIRMATIONS, false)
        assertTrue(vm.state.value.saveFailed)
        assertFalse(vm.state.value.isOn(NotificationToggle.PLAN_CONFIRMATIONS))
        vm.retrySave()
        assertFalse(vm.state.value.saveFailed)
        assertEquals(false, writer.lastFields["notificationPreferences.planConfirmations"])
    }

    @Test
    fun loadFailure_isVisible_butShowsTheCachedPreferences() {
        fetch = { throw IOException("offline") }
        val s = vm().state.value
        assertTrue(s.loadFailed)
        assertFalse(s.isOn(NotificationToggle.MESSAGES))
    }
}
