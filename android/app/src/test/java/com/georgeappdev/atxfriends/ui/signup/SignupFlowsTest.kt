package com.georgeappdev.atxfriends.ui.signup

import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.session.PendingSignup
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** The order accounts and `users` docs are created in, and cleanup when a step fails. */
class SignupFlowsTest {

    private val now = Instant.ofEpochSecond(1_770_000_000)
    private val austin = Coordinate(30.2672, -97.7431)
    private val events = mutableListOf<String>()
    private val docs = mutableListOf<Pair<String, NewDocument>>()

    private var authError: Exception? = null
    private var docError: Exception? = null
    private var abandonError: Exception? = null

    private fun emailCreator() = EmailAccountCreator(
        holdRouting = { block -> events += "hold"; try { block() } finally { events += "release" } },
        createAccount = { email, _ -> events += "auth:$email"; authError?.let { throw it }; "uid-new" },
        createUserDoc = { uid, doc -> events += "doc:$uid"; docError?.let { throw it }; docs += uid to doc; true },
        abandonAccount = { events += "deleteAccount"; abandonError?.let { throw it } },
        clock = { now },
    )

    @Test
    fun email_createsAuthAccountThenDoc_whileRoutingIsHeld() = runTest {
        emailCreator().create("sam@example.com", "secret1", austin)
        assertEquals(listOf("hold", "auth:sam@example.com", "doc:uid-new", "release"), events)
        val fields = docs.single().second.fields
        assertEquals("", fields["displayName"])
        assertEquals(30.27, fields["latitude"])
        assertEquals(false, fields["isProfileComplete"])
    }

    @Test
    fun email_authFailure_writesNoDoc() = runTest {
        authError = FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "x")
        try {
            emailCreator().create("sam@example.com", "secret1", austin)
            fail("should throw")
        } catch (e: FirebaseAuthException) {
            assertEquals("ERROR_EMAIL_ALREADY_IN_USE", e.errorCode)
        }
        assertEquals(listOf("hold", "auth:sam@example.com", "release"), events)
    }

    @Test
    fun email_docFailure_deletesTheNewAccount_andReportsTheFailure() = runTest {
        docError = IOException("offline")
        try {
            emailCreator().create("sam@example.com", "secret1", austin)
            fail("should throw")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        assertEquals(listOf("hold", "auth:sam@example.com", "doc:uid-new", "deleteAccount", "release"), events)
    }

    @Test
    fun email_docFailure_andCleanupFailure_stillReportsTheOriginalError() = runTest {
        docError = IOException("offline")
        abandonError = IOException("still offline")
        try {
            emailCreator().create("sam@example.com", "secret1", austin)
            fail("should throw")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        assertTrue("release" in events)
    }

    private var signedInUid: String? = "uid-g"

    private fun pendingOutcome(pending: PendingSignup) = PendingAccountOutcome(
        pending = { pending },
        currentUid = { signedInUid },
        createUserDoc = { uid, doc -> events += "doc:$uid"; docError?.let { throw it }; docs += uid to doc; true },
        abandonAccount = { events += "deleteAccount"; abandonError?.let { throw it } },
        resolved = { events += "resolved" },
        clock = { now },
    )

    @Test
    fun pending_gatePass_createsTheDocWithTheGoogleName_thenRoutes() = runTest {
        pendingOutcome(PendingSignup("uid-g", "Sam Rivera", "google")).passed(austin)
        assertEquals(listOf("doc:uid-g", "resolved"), events)
        assertEquals("Sam Rivera", docs.single().second.fields["displayName"])
    }

    @Test
    fun pending_docFailure_doesNotRoute() = runTest {
        docError = IOException("offline")
        try {
            pendingOutcome(PendingSignup("uid-g", "Sam", "google")).passed(austin)
            fail("should throw")
        } catch (_: IOException) {
        }
        assertEquals(listOf("doc:uid-g"), events)
    }

    @Test
    fun pending_abandon_deletesTheAuthAccount_thenRoutes() = runTest {
        pendingOutcome(PendingSignup("uid-g", "Sam", "google")).abandoned()
        assertEquals(listOf("deleteAccount", "resolved"), events)
        assertTrue(docs.isEmpty())
    }

    /** A gate left behind for an account that's gone must never write for it. */
    @Test
    fun pending_accountNoLongerSignedIn_writesAndDeletesNothing() = runTest {
        signedInUid = "someone-else"
        pendingOutcome(PendingSignup("uid-g", "Sam", "google")).passed(austin)
        pendingOutcome(PendingSignup("uid-g", "Sam", "google")).abandoned()
        assertEquals(listOf("resolved", "resolved"), events)
        assertTrue(docs.isEmpty())
    }
}
