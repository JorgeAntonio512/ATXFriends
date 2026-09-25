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
        )
        for ((name, text) in ios) assertEquals(name, text, strings[name])
    }

    private fun resourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    /** Reads res/values/strings.xml the way Android would render it (\' → ', \n → newline). */
    private fun loadStrings(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        assertTrue(file.absolutePath, file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to
                node.textContent.replace("\\'", "'").replace("\\n", "\n").replace("\\\"", "\"")
        }
    }
}
