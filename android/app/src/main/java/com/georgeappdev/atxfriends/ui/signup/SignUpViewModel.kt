package com.georgeappdev.atxfriends.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.georgeappdev.atxfriends.data.auth.AuthErrorMapper
import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.data.auth.AuthValidation
import com.georgeappdev.atxfriends.domain.location.Coordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: AuthMessage? = null,
)

/**
 * Port of iOS SignUpView + AuthViewModel.signUp. Only reachable after the location gate passed
 * with [coordinate]. On success nothing navigates: SessionManager sees the new account (with its
 * `users` doc) and shows profile setup.
 */
class SignUpViewModel(
    private val coordinate: Coordinate,
    private val createAccount: suspend (email: String, password: String, coordinate: Coordinate) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    // Editing any field clears the error, like iOS's onChange → clearError().
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return
        // Trimmed because Android keyboards often append a space after an autocompleted email.
        val email = current.email.trim()
        AuthValidation.signUpError(email, current.password, current.confirmPassword)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val failure = try {
                createAccount(email, current.password, coordinate)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthErrorMapper.messageFor(e)
            }
            // Fields are kept either way, so a failed attempt can be retried as-is.
            _state.update { it.copy(isLoading = false, error = failure) }
        }
    }
}
