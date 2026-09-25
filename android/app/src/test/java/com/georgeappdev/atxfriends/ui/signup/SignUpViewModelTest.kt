package com.georgeappdev.atxfriends.ui.signup

import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class SignUpViewModelTest {

    private val gateFix = Coordinate(30.3, -97.7)
    private val calls = mutableListOf<Triple<String, String, Coordinate>>()
    private var result: suspend () -> Unit = {}

    private fun vm() = SignUpViewModel(gateFix) { e, p, c -> calls += Triple(e, p, c); result() }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun SignUpViewModel.fill(email: String, password: String, confirm: String) {
        onEmailChange(email); onPasswordChange(password); onConfirmChange(confirm)
    }

    @Test
    fun validation_inIosOrder_withoutCallingFirebase() {
        val vm = vm()
        vm.fill("nope", "123", "456")
        vm.submit()
        assertEquals(AuthMessage.INVALID_EMAIL, vm.state.value.error)
        vm.fill("sam@example.com", "12345", "456")
        vm.submit()
        assertEquals(AuthMessage.PASSWORD_TOO_SHORT, vm.state.value.error)
        vm.fill("sam@example.com", "123456", "1234567")
        vm.submit()
        assertEquals(AuthMessage.PASSWORDS_DONT_MATCH, vm.state.value.error)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun valid_createsWithTheGateCoordinate() {
        val vm = vm()
        vm.fill("sam@example.com ", "123456", "123456")
        vm.submit()
        assertEquals(listOf(Triple("sam@example.com", "123456", gateFix)), calls)
        assertNull(vm.state.value.error)
    }

    @Test
    fun emailInUse_showsIosMessage_andKeepsTheForm() {
        result = { throw FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "x") }
        val vm = vm()
        vm.fill("sam@example.com", "123456", "123456")
        vm.submit()
        assertEquals(AuthMessage.EMAIL_IN_USE, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertEquals("sam@example.com", vm.state.value.email)
        assertEquals("123456", vm.state.value.password)
    }

    @Test
    fun docWriteFailure_isGeneric_likeIos() {
        result = { throw IOException("offline") }
        val vm = vm()
        vm.fill("sam@example.com", "123456", "123456")
        vm.submit()
        assertEquals(AuthMessage.GENERIC, vm.state.value.error)
    }

    @Test
    fun doubleTap_createsOnce() {
        val gate = CompletableDeferred<Unit>()
        result = { gate.await() }
        val vm = vm()
        vm.fill("sam@example.com", "123456", "123456")
        vm.submit()
        vm.submit()
        assertTrue(vm.state.value.isLoading)
        assertEquals(1, calls.size)
        gate.complete(Unit)
        assertFalse(vm.state.value.isLoading)
    }
}
