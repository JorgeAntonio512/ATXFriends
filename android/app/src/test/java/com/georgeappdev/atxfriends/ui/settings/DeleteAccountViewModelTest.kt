package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.repository.AccountDeleter
import com.georgeappdev.atxfriends.data.repository.AccountRepository
import com.georgeappdev.atxfriends.data.repository.AppleTokenRevoker
import com.georgeappdev.atxfriends.ui.auth.AppleReauth
import com.georgeappdev.atxfriends.ui.simpatico.SimpaticoPositionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.georgeappdev.atxfriends.ui.messages.FakeCalendarStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private class FakeDeleter : AccountDeleter {
        var calls = 0
        var error: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun deleteMyAccount(): List<String> {
            calls++
            gate?.await()
            error?.let { throw it }
            return listOf("plan-1")
        }
    }

    private class FakePositions : SimpaticoPositionStore {
        val map = mutableMapOf("uid-sam" to 4, "someone-else" to 2)
        override fun get(uid: String) = map[uid] ?: 0
        override fun save(uid: String, index: Int) { map[uid] = index }
        override fun clear(uid: String) { map.remove(uid) }
    }

    private class FakeApple(var isApple: Boolean = false) : AppleTokenRevoker {
        val revoked = mutableListOf<String>()
        var error: Exception? = null
        override fun isAppleAccount() = isApple
        override suspend fun revokeAccessToken(accessToken: String) {
            error?.let { throw it }
            revoked += accessToken
        }
    }

    private val deleter = FakeDeleter()
    private val apple = FakeApple()
    private var reauths = 0

    /** Apple's confirmation page: returns a fresh token, or null when closed. */
    private var applePage: suspend () -> String? = { "apple-token" }
    private val reauth = AppleReauth { reauths++; applePage() }
    private val positions = FakePositions()
    private var signedOut = 0
    private var uid: String? = "uid-sam"

    private val calendarFlags = FakeCalendarStore(mutableSetOf("plan-1", "plan-kept"))

    private fun vm() = DeleteAccountViewModel({ uid }, deleter, apple, positions, calendarFlags) { signedOut++ }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirmation_mustBeExactlyDELETE() {
        val vm = vm()
        for (text in listOf("", "delete", "DELET", "DELETE!", "Delete")) {
            vm.onConfirmationChange(text)
            assertFalse(text, vm.state.value.isConfirmed)
            vm.delete(reauth)
        }
        assertEquals("nothing is called until DELETE is typed", 0, deleter.calls)
        vm.onConfirmationChange("  DELETE ")
        assertTrue(vm.state.value.isConfirmed)
    }

    @Test
    fun success_callsTheFunctionOnce_clearsThisUsersLocalData_andSignsOut() {
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        assertEquals(1, deleter.calls)
        assertEquals(1, signedOut)
        assertFalse("uid-sam" in positions.map)
        assertEquals("other users' data is untouched", 2, positions.map["someone-else"])
        assertEquals("calendar flags cleared for the deleted plans only", setOf("plan-kept"), calendarFlags.added)
    }

    @Test
    fun whileDeleting_theButtonAndBackAreLocked_andDoubleTapsIgnored() {
        deleter.gate = CompletableDeferred()
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        vm.delete(reauth)
        assertTrue(vm.state.value.isDeleting)
        assertFalse(vm.state.value.canDelete)
        assertEquals(1, deleter.calls)
        deleter.gate!!.complete(Unit)
        assertEquals(1, signedOut)
    }

    @Test
    fun failure_showsTheError_staysSignedIn_clearsNothing_andCanRetry() {
        deleter.error = IOException("internal")
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        val s = vm.state.value
        assertTrue(s.failed)
        assertFalse(s.isDeleting)
        assertEquals("DELETE", s.confirmation)
        assertEquals(0, signedOut)
        assertEquals(4, positions.map["uid-sam"])

        deleter.error = null
        vm.dismissError()
        vm.delete(reauth)
        assertEquals(2, deleter.calls)
        assertEquals(1, signedOut)
    }

    @Test
    fun notSignedIn_showsTheError_withoutCalling() {
        uid = null
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        assertTrue(vm.state.value.failed)
        assertEquals(0, deleter.calls)
    }

    @Test
    fun nonAppleAccount_neverOpensApplesPage() {
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        assertEquals(0, reauths)
        assertTrue(apple.revoked.isEmpty())
        assertEquals(1, deleter.calls)
    }

    @Test
    fun appleAccount_revokesWithAFreshToken_thenDeletes() {
        apple.isApple = true
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        assertEquals(listOf("apple-token"), apple.revoked)
        assertEquals(1, deleter.calls)
        assertEquals(1, signedOut)
    }

    @Test
    fun appleAccount_closingApplesPage_cancelsTheDeletion() {
        apple.isApple = true
        applePage = { null }
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        val s = vm.state.value
        assertTrue(s.appleCancelled)
        assertFalse(s.isDeleting)
        assertEquals(0, deleter.calls)
        assertEquals(0, signedOut)

        vm.dismissError()
        assertFalse(vm.state.value.appleCancelled)
        applePage = { "apple-token" }
        vm.delete(reauth)
        assertEquals("retrying works", 1, deleter.calls)
    }

    /** iOS: a revocation failure (e.g. not configured in Firebase) never blocks deletion. */
    @Test
    fun appleAccount_revocationFailure_stillDeletes() {
        apple.isApple = true
        apple.error = IOException("OPERATION_NOT_ALLOWED")
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete(reauth)
        assertEquals(1, deleter.calls)
        assertEquals(1, signedOut)

        apple.error = null
        applePage = { throw IOException("Code flow is not enabled for Apple.") }
        val vm2 = vm()
        vm2.onConfirmationChange("DELETE")
        vm2.delete(reauth)
        assertEquals(2, deleter.calls)
        assertFalse(vm2.state.value.appleCancelled)
    }

    @Test
    fun callableResult_readsPlanIDsLeniently() {
        assertEquals(listOf("a", "b"), AccountRepository.planIDs(mapOf("planIDs" to listOf("a", "b"))))
        assertEquals(emptyList<String>(), AccountRepository.planIDs(null))
        assertEquals(emptyList<String>(), AccountRepository.planIDs(mapOf("planIDs" to "nope")))
        assertEquals("deleteMyAccount", AccountRepository.FUNCTION)
        assertEquals("us-central1", AccountRepository.REGION)
    }
}
