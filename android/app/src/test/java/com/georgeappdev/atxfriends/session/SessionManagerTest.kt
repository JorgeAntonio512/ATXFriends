package com.georgeappdev.atxfriends.session

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.AuthUser
import com.georgeappdev.atxfriends.data.repository.UserDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val sam = AuthUser("uid-sam", "sam@example.com")
    private val completeProfile = UserProfile.fromFirestore("uid-sam", TestDocs.user())!!
    private val incompleteProfile = completeProfile.copy(isProfileComplete = false)

    private fun TestScope.manager(user: MutableStateFlow<AuthUser?>, fetch: suspend (String) -> UserDoc) =
        SessionManager(user, fetch, { user.value = null }, backgroundScope)

    /** Unconfined, so state settles before each assertion. */
    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { body() }

    @Test
    fun signedOut_whenNoUser() = test {
        val m = manager(MutableStateFlow(null)) { error("must not read Firestore") }
        assertEquals(SessionState.SignedOut, m.state.value)
    }

    @Test
    fun ready_whenProfileComplete() = test {
        val m = manager(MutableStateFlow(sam)) { UserDoc.Found(completeProfile) }
        assertEquals(SessionState.Ready(sam, completeProfile), m.state.value)
    }

    @Test
    fun notFinished_forMissingUnreadableOrIncompleteProfiles() = test {
        for (doc in listOf(UserDoc.Missing, UserDoc.Unreadable, UserDoc.Found(incompleteProfile))) {
            val m = manager(MutableStateFlow(sam)) { doc }
            assertEquals(doc.toString(), SessionState.NotFinished(sam), m.state.value)
        }
    }

    @Test
    fun loadFailed_thenRetrySucceeds() = test {
        var fail = true
        val m = manager(MutableStateFlow(sam)) { if (fail) throw IOException("offline") else UserDoc.Found(completeProfile) }
        assertEquals(SessionState.LoadFailed(sam), m.state.value)
        fail = false
        m.retry()
        assertTrue(m.state.value is SessionState.Ready)
    }

    @Test
    fun signOut_returnsToSignedOut() = test {
        val m = manager(MutableStateFlow(sam)) { UserDoc.Found(completeProfile) }
        m.signOut()
        assertEquals(SessionState.SignedOut, m.state.value)
    }
}
