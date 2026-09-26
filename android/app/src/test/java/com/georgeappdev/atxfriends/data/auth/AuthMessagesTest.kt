package com.georgeappdev.atxfriends.data.auth

import com.georgeappdev.atxfriends.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AuthMessagesTest {

    @Test
    fun wrongPasswordAndUnknownEmailShareOneMessage() {
        for (code in listOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND")) {
            assertEquals(code, AuthMessage.INCORRECT_CREDENTIALS, AuthErrorMapper.messageFor(FirebaseAuthException(code, "x")))
        }
    }

    @Test
    fun otherFirebaseErrorsMapLikeIos() {
        assertEquals(AuthMessage.INVALID_EMAIL, AuthErrorMapper.messageFor(FirebaseAuthException("ERROR_INVALID_EMAIL", "x")))
        assertEquals(AuthMessage.USER_DISABLED, AuthErrorMapper.messageFor(FirebaseAuthException("ERROR_USER_DISABLED", "x")))
        assertEquals(AuthMessage.NETWORK, AuthErrorMapper.messageFor(FirebaseNetworkException("offline")))
        assertEquals(AuthMessage.TOO_MANY_REQUESTS, AuthErrorMapper.messageFor(FirebaseTooManyRequestsException("slow down")))
        assertEquals(AuthMessage.EMAIL_IN_USE, AuthErrorMapper.messageFor(FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "x")))
        assertEquals(AuthMessage.WEAK_PASSWORD, AuthErrorMapper.messageFor(FirebaseAuthException("ERROR_WEAK_PASSWORD", "x")))
        assertEquals(AuthMessage.GENERIC, AuthErrorMapper.messageFor(FirebaseAuthException("ERROR_SOMETHING_NEW", "x")))
        assertEquals(AuthMessage.GENERIC, AuthErrorMapper.messageFor(IllegalStateException()))
    }

    @Test
    fun validationMatchesIosOrderAndRegex() {
        assertEquals(AuthMessage.INVALID_EMAIL, AuthValidation.signInError("", ""))
        assertEquals(AuthMessage.INVALID_EMAIL, AuthValidation.signInError("sam@example", "pw"))
        assertEquals(AuthMessage.INVALID_EMAIL, AuthValidation.signInError("sam example.com", "pw"))
        assertEquals(AuthMessage.EMPTY_PASSWORD, AuthValidation.signInError("sam@example.com", ""))
        assertNull(AuthValidation.signInError("sam.o+atx@example.co", "pw"))
        assertEquals(AuthMessage.RESET_INVALID_EMAIL, AuthValidation.passwordResetError("nope"))
        assertNull(AuthValidation.passwordResetError("sam@example.com"))
    }

    /** AuthViewModel.validateSignUp: email, then ≥ 6 characters, then match. */
    @Test
    fun signUpValidationMatchesIos() {
        assertEquals(AuthMessage.INVALID_EMAIL, AuthValidation.signUpError("nope", "1", "2"))
        assertEquals(AuthMessage.PASSWORD_TOO_SHORT, AuthValidation.signUpError("sam@example.com", "12345", "12345"))
        assertEquals(AuthMessage.PASSWORDS_DONT_MATCH, AuthValidation.signUpError("sam@example.com", "123456", "123457"))
        assertNull(AuthValidation.signUpError("sam@example.com", "123456", "123456"))
        // Characters as people see them (Swift String.count): five emoji are still too short.
        assertEquals(AuthMessage.PASSWORD_TOO_SHORT, AuthValidation.signUpError("sam@example.com", "🌮🌮🌮🌮🌮", "🌮🌮🌮🌮🌮"))
    }

    @Test
    fun onlyTheCombinedCredentialsErrorOffersAccountCreation() {
        assertEquals(setOf(AuthMessage.INCORRECT_CREDENTIALS), AuthMessage.entries.filter { it.suggestsAccountCreation }.toSet())
    }

    /** The wording shown on screen must be exactly what iOS shows. */
    @Test
    fun messageTextMatchesIosWordingExactly() {
        val ios = mapOf(
            AuthMessage.INCORRECT_CREDENTIALS to "Email or password is incorrect.",
            AuthMessage.INVALID_EMAIL to "That doesn't look like a valid email address.",
            AuthMessage.NETWORK to "Can't connect right now. Check your internet and try again.",
            AuthMessage.TOO_MANY_REQUESTS to "Too many attempts. Wait a few minutes and try again.",
            AuthMessage.USER_DISABLED to "This account has been disabled.",
            AuthMessage.GENERIC to "Something went wrong. Please try again.",
            AuthMessage.EMPTY_PASSWORD to "Please enter your password.",
            AuthMessage.RESET_INVALID_EMAIL to "Please enter a valid email address.",
            AuthMessage.RESET_FAILED to "Failed to send password reset email. Please try again.",
            AuthMessage.EMAIL_IN_USE to "An account with this email already exists. Try signing in instead.",
            AuthMessage.WEAK_PASSWORD to "That password is too weak. Try a longer one.",
            AuthMessage.PASSWORD_TOO_SHORT to "Password must be at least 6 characters.",
            AuthMessage.PASSWORDS_DONT_MATCH to "Passwords do not match.",
            // Android-only (iOS shows nothing).
            AuthMessage.GOOGLE_FAILED to "Couldn't sign in with Google. Please try again.",
            AuthMessage.APPLE_FAILED to "Couldn't sign in with Apple. Please try again.",
        )
        assertEquals(AuthMessage.entries.toSet(), ios.keys)
        val strings = loadStrings()
        for ((message, text) in ios) {
            assertEquals(message.name, text, strings[resourceName(message.text)])
        }
    }

    @Test
    fun screenStringsMatchIosWordingExactly() {
        val strings = loadStrings()
        val ios = mapOf(
            "welcome_tagline" to "Find Your People",
            "welcome_subtitle" to "Build meaningful friendships\nin Austin, TX",
            "welcome_feature_friendship" to "Not a dating app — genuine friendships only",
            "welcome_feature_matching" to "Match based on activities & availability",
            "welcome_feature_neighbors" to "Connect with neighbors in Austin",
            "action_sign_in" to "Sign In",
            "action_back" to "Back",
            "sign_in_title" to "Welcome Back",
            "sign_in_subtitle" to "Sign in to continue",
            "field_email" to "Email",
            "field_email_placeholder" to "you@example.com",
            "field_password" to "Password",
            "field_password_placeholder" to "Enter your password",
            "forgot_password" to "Forgot Password?",
            "reset_title" to "Reset Password",
            "reset_body" to "Enter your email and we'll send you instructions to reset your password.",
            "reset_send" to "Send Reset Email",
            "action_cancel" to "Cancel",
            "reset_success_title" to "Password Reset Email Sent",
            "reset_success_body" to "Check your email for instructions to reset your password.",
            "action_ok" to "OK",
            "sign_out" to "Sign Out",
            "sign_out_confirm" to "Are you sure you want to sign out?",
            // OnboardingView / SignInView sign-up half.
            "welcome_continue_google" to "Continue with Google",
            "welcome_or" to "or",
            "welcome_register" to "Register",
            "sign_in_create_account" to "New here? Create an account",
            // LocationGateView, LocationPermissionNeededView, WaitlistView.
            "gate_title" to "One quick check",
            "gate_body" to "ATX Friends is Austin-only for now — we need to confirm you're nearby before creating your account.",
            "gate_privacy_title" to "Your Privacy",
            "gate_privacy_body" to "We only check your location once, at signup. We don't track you after that.",
            "action_continue" to "Continue",
            "gate_timeout" to "Couldn't get your location in time.",
            "permission_needed_title" to "We need your location",
            "permission_needed_body" to "We need your location to confirm you're in the Austin area. Open Settings and turn on Location for ATX Friends, then come back — we'll pick up right where you left off.",
            "permission_needed_open_settings" to "Open Settings",
            "action_not_now" to "Not now",
            "waitlist_title" to "ATX Friends is Austin-only for now",
            "waitlist_body" to "We're starting in Austin, TX. Leave your email and we'll notify you when we expand to your area.",
            "waitlist_submit" to "Notify me when you expand",
            "waitlist_success_title" to "You're on the list!",
            "waitlist_success_body" to "We'll email you when ATX Friends launches in your area.",
            "waitlist_failed" to "Failed to sign up — please try again.",
            "account_error_title" to "Account Error",
            "account_error_create_failed" to "Failed to create account. Please try again.",
            // SignUpView.
            "sign_up_title" to "Welcome to ATX Friends",
            "sign_up_subtitle" to "Let's create your account",
            "sign_up_password_placeholder" to "At least 6 characters",
            "sign_up_confirm_label" to "Confirm Password",
            "sign_up_confirm_placeholder" to "Re-enter password",
            "sign_up_button" to "Create Account",
            "sign_up_next_title" to "What happens next?",
            "sign_up_next_body" to "After creating your account, you'll set up your profile with 3 photos, 3+ activities, and 3+ time slots.",
            "sign_up_terms" to "By creating an account, you agree to our\nTerms of Service and Privacy Policy",
        )
        for ((name, text) in ios) assertEquals(name, text, strings[name])
    }

    private fun resourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    /** Reads res/values/strings*.xml the way Android would render it (\' → ', \n → newline). */
    private fun loadStrings(): Map<String, String> {
        val files = File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        assertTrue(File("src/main/res/values").absolutePath, files.isNotEmpty())
        return files.map { loadStrings(it) }.reduce { a, b -> a + b }
    }

    private fun loadStrings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to
                node.textContent.replace("\\'", "'").replace("\\n", "\n").replace("\\\"", "\"")
        }
    }
}
