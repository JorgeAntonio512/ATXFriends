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
    fun incompleteProfile_goesToProfileSetup() = test {
        val m = manager(MutableStateFlow(sam)) { UserDoc.Found(incompleteProfile) }
        assertEquals(SessionState.NeedsProfileSetup(sam, incompleteProfile), m.state.value)
    }

    @Test
    fun unreadableDoc_isNeverRecreated() = test {
        val m = manager(MutableStateFlow(sam)) { UserDoc.Unreadable }
        assertEquals(SessionState.NotFinished(sam), m.state.value)
    }

    /** iOS RootView orphan recovery: signed in with no doc (app killed mid-gate) → the gate. */
    @Test
    fun missingDoc_resumesAtTheLocationGate_withTheAuthName() = test {
        val google = AuthUser("uid-g", "sam@gmail.com", "Sam Rivera")
        val m = manager(MutableStateFlow(google)) { UserDoc.Missing }
        assertEquals(SessionState.NeedsLocationGate(google, PendingSignup("uid-g", "Sam Rivera", "resume")), m.state.value)
    }

    @Test
    fun missingDoc_forAnEmailAccount_resumesWithNoName_neverTheEmail() = test {
        val emailOnly = AuthUser("uid-e", "sam@example.com", null)
        val m = manager(MutableStateFlow(emailOnly)) { UserDoc.Missing }
        assertEquals(PendingSignup("uid-e", "", "resume"), (m.state.value as SessionState.NeedsLocationGate).pending)
    }

    @Test
    fun newGoogleAccount_pendingSignupIsUsed_andResolvingRoutesToSetup() = test {
        val google = AuthUser("uid-g", "sam@gmail.com", "Sam Rivera")
        var doc: UserDoc = UserDoc.Missing
        val m = manager(MutableStateFlow(google)) { doc }
        m.startPendingSignup(PendingSignup("uid-g", "Sam R.", "google"))
        assertEquals(PendingSignup("uid-g", "Sam R.", "google"), (m.state.value as SessionState.NeedsLocationGate).pending)

        doc = UserDoc.Found(incompleteProfile) // the gate passed and wrote the doc
        m.pendingSignupResolved()
        assertTrue(m.state.value is SessionState.NeedsProfileSetup)
    }

    @Test
    fun aPendingSignupForAnotherAccount_isIgnored() = test {
        val m = manager(MutableStateFlow(sam)) { UserDoc.Missing }
        m.startPendingSignup(PendingSignup("someone-else", "X", "google"))
        assertEquals("uid-sam", (m.state.value as SessionState.NeedsLocationGate).pending.uid)
        assertEquals("resume", (m.state.value as SessionState.NeedsLocationGate).pending.path)
    }

    /** Email sign-up: the Auth account exists a moment before its doc; nothing may route yet. */
    @Test
    fun holdRouting_keepsTheSignUpScreen_untilTheDocIsWritten() = test {
        val user = MutableStateFlow<AuthUser?>(null)
        var doc: UserDoc = UserDoc.Missing
        var reads = 0
        val m = manager(user) { reads++; doc }
        m.holdRoutingWhile {
            user.value = sam // createUser succeeded
            assertEquals(SessionState.SignedOut, m.state.value)
            doc = UserDoc.Found(incompleteProfile) // users doc written
            assertEquals(SessionState.SignedOut, m.state.value)
        }
        assertEquals("never read the half-made account", 1, reads)
        assertEquals(SessionState.NeedsProfileSetup(sam, incompleteProfile), m.state.value)
    }

    @Test
    fun holdRouting_whenSignUpRolledBack_endsSignedOut() = test {
        val user = MutableStateFlow<AuthUser?>(null)
        val m = manager(user) { error("must not read Firestore") }
        runCatching {
            m.holdRoutingWhile {
                user.value = sam
                user.value = null // doc failed → account deleted
                throw IOException("offline")
            }
        }
        assertEquals(SessionState.SignedOut, m.state.value)
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
