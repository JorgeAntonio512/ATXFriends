package com.georgeappdev.atxfriends.ui.auth

import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
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

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val signInCalls = mutableListOf<Pair<String, String>>()
    private var signInError: Exception? = null
    private var resetError: Exception? = null

    private fun viewModel() = SignInViewModel(
        signIn = { e, p -> signInCalls += e to p; signInError?.let { throw it } },
        sendPasswordReset = { resetError?.let { throw it } },
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun invalidInput_showsIosValidationMessage_withoutCallingFirebase() {
        val vm = viewModel()
        vm.submit()
        assertEquals(AuthMessage.INVALID_EMAIL, vm.state.value.error)
        vm.onEmailChange("sam@example.com")
        assertNull("editing clears the error", vm.state.value.error)
        vm.submit()
        assertEquals(AuthMessage.EMPTY_PASSWORD, vm.state.value.error)
        assertTrue(signInCalls.isEmpty())
    }

    @Test
    fun wrongPassword_showsCombinedMessage() {
        signInError = FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "x")
        val vm = viewModel()
        vm.onEmailChange("sam@example.com ")
        vm.onPasswordChange("nope")
        vm.submit()
        assertEquals(listOf("sam@example.com" to "nope"), signInCalls)
        assertEquals(AuthMessage.INCORRECT_CREDENTIALS, vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun networkFailure_andSuccess() {
        signInError = FirebaseNetworkException("offline")
        val vm = viewModel()
        vm.onEmailChange("sam@example.com")
        vm.onPasswordChange("pw")
        vm.submit()
        assertEquals(AuthMessage.NETWORK, vm.state.value.error)

        signInError = null
        vm.submit()
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun passwordReset_flow() {
        val vm = viewModel()
        vm.onEmailChange("sam@example.com")
        vm.openReset()
        assertEquals("sam@example.com", vm.state.value.reset?.email)

        vm.onResetEmailChange("bad")
        vm.sendReset()
        assertEquals(AuthMessage.RESET_INVALID_EMAIL, vm.state.value.reset?.error)

        vm.onResetEmailChange("sam@example.com")
        resetError = RuntimeException("boom")
        vm.sendReset()
        assertEquals(AuthMessage.RESET_FAILED, vm.state.value.reset?.error)

        resetError = null
        vm.sendReset()
        assertNull("sheet closes on success", vm.state.value.reset)
        assertTrue(vm.state.value.showResetSuccess)
        vm.dismissResetSuccess()
        assertFalse(vm.state.value.showResetSuccess)
    }
}
