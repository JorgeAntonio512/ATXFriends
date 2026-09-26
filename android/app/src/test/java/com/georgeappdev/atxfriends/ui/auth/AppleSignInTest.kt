package com.georgeappdev.atxfriends.ui.auth

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.AppleNames
import com.georgeappdev.atxfriends.data.repository.AppleSignInResult
import com.georgeappdev.atxfriends.data.repository.AuthUser
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.session.PendingSignup
import com.georgeappdev.atxfriends.session.SessionManager
import com.georgeappdev.atxfriends.session.SessionState
import com.georgeappdev.atxfriends.ui.signup.PendingAccountOutcome
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/** Sign in with Apple: new vs returning accounts, the one-time name, and abandoned sign-ups. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppleSignInTest {

    private val pendings = mutableListOf<PendingSignup>()
    private val savedNames = mutableListOf<String>()
    private var saveError: Exception? = null
    private val handler = AppleSignInHandler(
        startPendingSignup = { pendings += it },
        saveName = { savedNames += it; saveError?.let { e -> throw e } },
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // New vs returning

    @Test
    fun newAccount_waitsAtTheGate_withApplesName() = runTest {
        handler.signedIn(AppleSignInResult("uid-a", isNewUser = true, displayName = "Sam Rivera"))
        assertEquals(listOf(PendingSignup("uid-a", "Sam Rivera", "apple")), pendings)
        assertEquals("kept on the Auth account for a relaunch mid-sign-up", listOf("Sam Rivera"), savedNames)
    }

    @Test
    fun newAccount_withoutAName_startsWithAnEmptyName_andSavesNothing() = runTest {
        handler.signedIn(AppleSignInResult("uid-a", isNewUser = true, displayName = ""))
        assertEquals(listOf(PendingSignup("uid-a", "", "apple")), pendings)
        assertTrue(savedNames.isEmpty())
    }

    @Test
    fun newAccount_nameSaveFailure_stillGoesToTheGate() = runTest {
        saveError = IOException("offline")
        handler.signedIn(AppleSignInResult("uid-a", isNewUser = true, displayName = "Sam"))
        assertEquals(listOf(PendingSignup("uid-a", "Sam", "apple")), pendings)
    }

    @Test
    fun returningAccount_goesStraightIn() = runTest {
        handler.signedIn(AppleSignInResult("uid-a", isNewUser = false, displayName = ""))
        assertTrue(pendings.isEmpty())
        assertTrue(savedNames.isEmpty())
    }

    @Test
    fun routing_newAccountToTheGateWithTheName_returningAccountToTheTabs() = runTest(UnconfinedTestDispatcher()) {
        val apple = AuthUser("uid-a", "abc123@privaterelay.appleid.com", null)
        val complete = UserProfile.fromFirestore("uid-a", TestDocs.user())!!

        val newUser = SessionManager(MutableStateFlow(apple), { UserDoc.Missing }, {}, backgroundScope)
        AppleSignInHandler(newUser::startPendingSignup) {}.signedIn(AppleSignInResult("uid-a", true, "Sam Rivera"))
        val gate = newUser.state.value as SessionState.NeedsLocationGate
        assertEquals(PendingSignup("uid-a", "Sam Rivera", "apple"), gate.pending)

        val returning = SessionManager(MutableStateFlow(apple), { UserDoc.Found(complete) }, {}, backgroundScope)
        AppleSignInHandler(returning::startPendingSignup) {}.signedIn(AppleSignInResult("uid-a", false, ""))
        assertTrue(returning.state.value is SessionState.Ready)
    }

    @Test
    fun relaunchMidSignUp_neverUsesTheRelayAddressAsAName() = runTest(UnconfinedTestDispatcher()) {
        val apple = AuthUser("uid-a", "abc123@privaterelay.appleid.com", "abc123@privaterelay.appleid.com")
        val m = SessionManager(MutableStateFlow(apple), { UserDoc.Missing }, {}, backgroundScope)
        assertEquals("", (m.state.value as SessionState.NeedsLocationGate).pending.displayName)
    }

    // The button

    @Test
    fun button_closedApplePage_endsQuietly() {
        val vm = AppleSignInViewModel(handler)
        vm.signIn { null }
        assertEquals(AppleSignInUiState(), vm.state.value)
        assertTrue(pendings.isEmpty())
    }

    @Test
    fun button_newAccount_handsOffToTheGate() {
        val vm = AppleSignInViewModel(handler)
        vm.signIn { AppleSignInResult("uid-a", true, "Sam") }
        assertFalse(vm.state.value.isLoading)
        assertEquals("uid-a", pendings.single().uid)
    }

    @Test
    fun button_blocksASecondTapWhileApplesPageIsOpen() {
        val page = CompletableDeferred<AppleSignInResult?>()
        var opened = 0
        val vm = AppleSignInViewModel(handler)
        vm.signIn { opened++; page.await() }
        vm.signIn { opened++; null }
        assertTrue(vm.state.value.isLoading)
        assertEquals(1, opened)
        page.complete(null)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun button_failuresShowAMessage() {
        val vm = AppleSignInViewModel(handler)
        vm.signIn { throw FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "Code flow is not enabled for Apple.") }
        assertEquals(AuthMessage.APPLE_FAILED, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)

        vm.signIn { throw FirebaseNetworkException("offline") }
        assertEquals(AuthMessage.NETWORK, vm.state.value.error)
        vm.clearError()
        assertNull(vm.state.value.error)
    }

    // Name capture

    @Test
    fun name_takenFromWhereverFirebasePutsIt() {
        assertEquals("Sam Rivera", AppleNames.fromSignIn("Sam Rivera", null, null))
        assertEquals("Sam Rivera", AppleNames.fromSignIn(null, "Sam Rivera", null))
        assertEquals("Sam Rivera", AppleNames.fromSignIn(null, null, mapOf("name" to mapOf("firstName" to "Sam", "lastName" to "Rivera"))))
        assertEquals("Sam Rivera", AppleNames.fromSignIn(null, null, mapOf("firstName" to "Sam", "lastName" to "Rivera")))
        assertEquals("Sam", AppleNames.fromSignIn("", null, mapOf("given_name" to "Sam")))
        assertEquals("Sam Rivera", AppleNames.fromSignIn(null, null, mapOf("fullName" to " Sam Rivera ")))
    }

    @Test
    fun name_neverAnEmailOrRelayAddress() {
        val relay = "abc123@privaterelay.appleid.com"
        val profile = mapOf("email" to relay, "sub" to "001234.abcd", "is_private_email" to "true")
        assertEquals("", AppleNames.fromSignIn(relay, relay, profile))
        assertEquals("", AppleNames.fromSignIn(null, "sam@icloud.com", mapOf("name" to relay)))
        assertEquals("Sam", AppleNames.fromSignIn(relay, null, mapOf("firstName" to "Sam")))
        assertEquals("", AppleNames.fromSignIn(null, null, null))
    }

    // The gate, for an Apple sign-up

    private val events = mutableListOf<String>()
    private val docs = mutableListOf<Pair<String, NewDocument>>()
    private var signedInUid: String? = "uid-a"

    private fun outcome(pending: PendingSignup) = PendingAccountOutcome(
        pending = { pending },
        currentUid = { signedInUid },
        createUserDoc = { uid, doc -> events += "doc:$uid"; docs += uid to doc; true },
        abandonAccount = { events += "deleteAccount" },
        resolved = { events += "resolved" },
        clock = { Instant.ofEpochSecond(1_770_000_000) },
    )

    @Test
    fun gatePass_createsTheDocWithApplesName() = runTest {
        outcome(PendingSignup("uid-a", "Sam Rivera", "apple")).passed(Coordinate(30.2672, -97.7431))
        assertEquals(listOf("doc:uid-a", "resolved"), events)
        assertEquals("Sam Rivera", docs.single().second.fields["displayName"])
    }

    @Test
    fun backingOut_deletesThePendingAuthAccount_andWritesNoDoc() = runTest {
        outcome(PendingSignup("uid-a", "Sam", "apple")).abandoned()
        assertEquals(listOf("deleteAccount", "resolved"), events)
        assertTrue(docs.isEmpty())
    }
}
