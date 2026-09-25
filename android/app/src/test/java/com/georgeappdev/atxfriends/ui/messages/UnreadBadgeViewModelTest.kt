package com.georgeappdev.atxfriends.ui.messages

import com.georgeappdev.atxfriends.data.repository.UnreadMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class UnreadBadgeViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun unreadCount_readsTheQueryLikeIosUnreadState() {
        val docs = listOf(
            mapOf("matchID" to "a", "isRead" to false),
            mapOf("matchID" to "a", "isRead" to false),
            mapOf("matchID" to "b", "isRead" to false),
            mapOf("eventID" to "e1", "isRead" to false), // dead event DM: no matchID, doesn't count
        )
        assertEquals(setOf("a", "b"), UnreadMessages.matchIDs(docs))
        assertTrue(UnreadMessages.showsTabDot(setOf("a")))
        assertFalse(UnreadMessages.showsTabDot(emptySet()))
        assertFalse("only event DMs unread: no dot, as on iOS", UnreadMessages.showsTabDot(UnreadMessages.matchIDs(docs.takeLast(1))))
    }

    @Test
    fun dotFollowsTheLiveQuery_andTheSignedInUser() = runTest {
        val uid = MutableStateFlow<String?>("me")
        val unread = mapOf("me" to MutableStateFlow(setOf("m1")), "you" to MutableStateFlow(emptySet<String>()))
        val vm = UnreadBadgeViewModel(uid) { unread.getValue(it) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showsMessagesDot.collect {} }

        assertTrue(vm.showsMessagesDot.value)
        unread.getValue("me").value = emptySet()
        assertFalse("opening the thread cleared it", vm.showsMessagesDot.value)
        unread.getValue("me").value = setOf("m2")
        assertTrue(vm.showsMessagesDot.value)
        uid.value = null
        assertFalse("signed out", vm.showsMessagesDot.value)
        uid.value = "you"
        assertFalse("next user's own unread state", vm.showsMessagesDot.value)
    }

    @Test
    fun listenerError_keepsTheLastState() = runTest {
        val vm = UnreadBadgeViewModel(MutableStateFlow("me")) {
            flow {
                emit(setOf("m1"))
                throw IOException("denied")
            }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showsMessagesDot.collect {} }
        assertTrue(vm.showsMessagesDot.value)
    }
}
