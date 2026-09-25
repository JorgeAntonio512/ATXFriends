package com.georgeappdev.atxfriends.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.auth.AuthErrorMapper
import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.data.auth.AuthValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthMessage? = null,
    val reset: ResetUiState? = null,
    val showResetSuccess: Boolean = false,
)

/** The Forgot Password sheet, while it's open. */
data class ResetUiState(
    val email: String,
    val isLoading: Boolean = false,
    val error: AuthMessage? = null,
)

/**
 * Port of the iOS AuthViewModel sign-in and password-reset paths. On success nothing needs
 * to navigate: SessionManager sees the new Firebase session and swaps in the main app.
 */
class SignInViewModel(
    private val signIn: suspend (email: String, password: String) -> Unit,
    private val sendPasswordReset: suspend (email: String) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    // Editing either field clears the error, like iOS's onChange → clearError().
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        // Trimmed because Android keyboards often append a space after an autocompleted email.
        val email = current.email.trim()
        AuthValidation.signInError(email, current.password)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val failure = attempt { signIn(email, current.password) }
            _state.update { it.copy(isLoading = false, error = failure?.let(AuthErrorMapper::messageFor)) }
        }
    }

    fun openReset() = _state.update { it.copy(reset = ResetUiState(email = it.email.trim())) }
    fun dismissReset() = _state.update { it.copy(reset = null) }
    fun onResetEmailChange(value: String) =
        _state.update { s -> s.copy(reset = s.reset?.copy(email = value, error = null)) }

    fun sendReset() {
        val reset = _state.value.reset ?: return
        if (reset.isLoading) return
        val email = reset.email.trim()
        AuthValidation.passwordResetError(email)?.let { message ->
            _state.update { s -> s.copy(reset = s.reset?.copy(error = message)) }
            return
        }
        _state.update { s -> s.copy(reset = s.reset?.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            val failure = attempt { sendPasswordReset(email) }
            _state.update { s ->
                if (failure == null) s.copy(reset = null, showResetSuccess = true)
                else s.copy(reset = s.reset?.copy(isLoading = false, error = AuthMessage.RESET_FAILED))
            }
        }
    }

    fun dismissResetSuccess() = _state.update { it.copy(showResetSuccess = false) }

    private suspend fun attempt(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val auth = (this[APPLICATION_KEY] as AtxFriendsApp).container.auth
                SignInViewModel(auth::signIn, auth::sendPasswordReset)
            }
        }
    }
}
