package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.repository.AccountDeleter
import com.georgeappdev.atxfriends.data.repository.AccountRepository
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

    private val deleter = FakeDeleter()
    private val positions = FakePositions()
    private var signedOut = 0
    private var uid: String? = "uid-sam"

    private val calendarFlags = FakeCalendarStore(mutableSetOf("plan-1", "plan-kept"))

    private fun vm() = DeleteAccountViewModel({ uid }, deleter, positions, calendarFlags) { signedOut++ }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirmation_mustBeExactlyDELETE() {
        val vm = vm()
        for (text in listOf("", "delete", "DELET", "DELETE!", "Delete")) {
            vm.onConfirmationChange(text)
            assertFalse(text, vm.state.value.isConfirmed)
            vm.delete()
        }
        assertEquals("nothing is called until DELETE is typed", 0, deleter.calls)
        vm.onConfirmationChange("  DELETE ")
        assertTrue(vm.state.value.isConfirmed)
    }

    @Test
    fun success_callsTheFunctionOnce_clearsThisUsersLocalData_andSignsOut() {
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete()
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
        vm.delete()
        vm.delete()
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
        vm.delete()
        val s = vm.state.value
        assertTrue(s.failed)
        assertFalse(s.isDeleting)
        assertEquals("DELETE", s.confirmation)
        assertEquals(0, signedOut)
        assertEquals(4, positions.map["uid-sam"])

        deleter.error = null
        vm.dismissError()
        vm.delete()
        assertEquals(2, deleter.calls)
        assertEquals(1, signedOut)
    }

    @Test
    fun notSignedIn_showsTheError_withoutCalling() {
        uid = null
        val vm = vm()
        vm.onConfirmationChange("DELETE")
        vm.delete()
        assertTrue(vm.state.value.failed)
        assertEquals(0, deleter.calls)
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
