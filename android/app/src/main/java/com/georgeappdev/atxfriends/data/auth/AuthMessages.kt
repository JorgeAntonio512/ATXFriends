package com.georgeappdev.atxfriends.data.auth

import androidx.annotation.StringRes
import com.georgeappdev.atxfriends.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/** Every sign-in / password-reset message, worded exactly as iOS shows it. */
enum class AuthMessage(@param:StringRes val text: Int) {
    // AuthErrorMessage.swift
    INCORRECT_CREDENTIALS(R.string.auth_error_incorrect_credentials),
    INVALID_EMAIL(R.string.auth_error_invalid_email),
    NETWORK(R.string.auth_error_network),
    TOO_MANY_REQUESTS(R.string.auth_error_too_many_requests),
    USER_DISABLED(R.string.auth_error_user_disabled),
    GENERIC(R.string.auth_error_generic),

    // AuthViewModel.swift validation and password reset
    EMPTY_PASSWORD(R.string.auth_error_empty_password),
    RESET_INVALID_EMAIL(R.string.auth_reset_error_invalid_email),
    RESET_FAILED(R.string.auth_reset_error_failed),
}

/** Port of iOS `AuthErrorMapper` (sign-in cases only — Android has no sign-up yet). */
object AuthErrorMapper {

    /**
     * Wrong password and unknown email deliberately share one message, so the screen can't be
     * used to probe which emails have accounts.
     */
    fun messageFor(error: Throwable): AuthMessage = when (error) {
        is FirebaseNetworkException -> AuthMessage.NETWORK
        is FirebaseTooManyRequestsException -> AuthMessage.TOO_MANY_REQUESTS
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                AuthMessage.INCORRECT_CREDENTIALS
            "ERROR_INVALID_EMAIL" -> AuthMessage.INVALID_EMAIL
            "ERROR_USER_DISABLED" -> AuthMessage.USER_DISABLED
            "ERROR_TOO_MANY_REQUESTS" -> AuthMessage.TOO_MANY_REQUESTS
            else -> AuthMessage.GENERIC
        }
        else -> AuthMessage.GENERIC
    }
}

/** Same checks, same order, same regex as iOS `AuthViewModel`. */
object AuthValidation {
    private val emailRegex = Regex("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}")

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

    /** Null when the form may be submitted. */
    fun signInError(email: String, password: String): AuthMessage? = when {
        !isValidEmail(email) -> AuthMessage.INVALID_EMAIL
        password.isEmpty() -> AuthMessage.EMPTY_PASSWORD
        else -> null
    }

    fun passwordResetError(email: String): AuthMessage? =
        if (isValidEmail(email)) null else AuthMessage.RESET_INVALID_EMAIL
}
