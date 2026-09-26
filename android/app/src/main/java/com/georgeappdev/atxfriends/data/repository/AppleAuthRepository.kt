package com.georgeappdev.atxfriends.data.repository

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Result of a finished Sign in with Apple. [displayName] is "" when Apple shared no usable name. */
data class AppleSignInResult(val uid: String, val isNewUser: Boolean, val displayName: String)

/** Sign in with Apple, from the Delete Account screen's point of view (a seam for tests). */
interface AppleTokenRevoker {
    /** iOS AccountDeletionService.isSignedInWithApple: `providerData` contains `apple.com`. */
    fun isAppleAccount(): Boolean

    /** Revokes the app's Apple sign-in, as Apple requires on account deletion. Throws on failure. */
    suspend fun revokeAccessToken(accessToken: String)
}

/**
 * Sign in with Apple on Android. There's no Apple SDK here: Firebase Auth runs Apple's web
 * sign-in in a Custom Tab (`OAuthProvider("apple.com")`) and returns to the app signed in.
 * Needs the Services ID, Team ID, Key ID and private key in the Firebase console's Apple
 * provider ("OAuth code flow configuration") — without them Firebase refuses with
 * OPERATION_NOT_ALLOWED. iOS's native sign-in doesn't need them, which is why iOS works anyway.
 */
class AppleAuthRepository(private val auth: FirebaseAuth) : AppleTokenRevoker {

    /**
     * Opens Apple's sign-in page and signs in to Firebase. Returns null when the person closes
     * the page. Throws a Firebase exception on any other failure.
     */
    suspend fun signIn(activity: Activity): AppleSignInResult? = webFlow("sign-in") {
        auth.startActivityForSignInWithProvider(activity, provider()).await()
    }?.let(::toResult)

    /**
     * A sign-in that finished while this app's activity was gone (Android reclaimed it while
     * Apple's page was open). Firebase has already signed the account in; this recovers
     * `isNewUser` and the first-sign-in name, which only this result carries.
     */
    suspend fun pendingSignIn(): AppleSignInResult? {
        val pending = auth.pendingAuthResult ?: return null
        Log.i(TAG, "apple: recovering a sign-in that finished in the background")
        return try {
            val result = pending.await()
            if (result.credential?.provider == PROVIDER_ID) toResult(result) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "apple: background sign-in failed", e)
            null
        }
    }

    /**
     * Stores Apple's one-time name on the Firebase Auth account (not Firestore), so a sign-up
     * interrupted before the location gate still has it on relaunch (SessionManager resumes
     * from the Auth display name). Only when Firebase didn't already store one.
     */
    suspend fun saveNameIfMissing(name: String) {
        val user = auth.currentUser ?: return
        if (name.isEmpty() || !user.displayName.isNullOrBlank()) return
        withWriteTimeout { user.updateProfile(userProfileChangeRequest { displayName = name }).await() }
        Log.i(TAG, "apple: saved first-sign-in name on the auth account")
    }

    override fun isAppleAccount(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == PROVIDER_ID } ?: false

    /**
     * iOS re-authenticates with Apple for a fresh authorization code before deleting. Android's
     * equivalent is a fresh Apple *access token* from re-authenticating through the same web
     * flow. Returns null when the person closes Apple's page.
     */
    suspend fun freshAccessToken(activity: Activity): String? {
        val user = auth.currentUser ?: return null
        val result = webFlow("re-auth") {
            user.startActivityForReauthenticateWithProvider(activity, provider()).await()
        } ?: return null
        return requireNotNull((result.credential as? OAuthCredential)?.accessToken) { "Apple returned no access token" }
    }

    /** `FirebaseAuth.revokeAccessToken` — Firebase's Android API, Apple access tokens only. */
    override suspend fun revokeAccessToken(accessToken: String) {
        withWriteTimeout { auth.revokeAccessToken(accessToken).await() }
        Log.i(TAG, "apple: revoked the app's Apple sign-in")
    }

    /** iOS requests `[.fullName, .email]`. */
    private fun provider(): OAuthProvider =
        OAuthProvider.newBuilder(PROVIDER_ID).setScopes(listOf("email", "name")).build()

    /** Runs a Custom Tab flow; null when the person closed it. */
    private suspend fun webFlow(what: String, block: suspend () -> AuthResult): AuthResult? {
        Log.i(TAG, "apple: $what — opening Apple's page")
        return try {
            block().also { Log.i(TAG, "apple: $what finished") }
        } catch (e: FirebaseAuthException) {
            if (e.errorCode == CANCELED) {
                Log.i(TAG, "apple: $what — page closed")
                null
            } else {
                Log.w(TAG, "apple: $what failed (${e.errorCode})", e)
                throw e
            }
        }
    }

    private fun toResult(result: AuthResult): AppleSignInResult {
        val user = requireNotNull(result.user) { "No user after Apple sign-in" }
        val isNew = result.additionalUserInfo?.isNewUser ?: false
        val name = AppleNames.fromSignIn(
            firebaseDisplayName = user.displayName,
            providerDisplayName = user.providerData.firstOrNull { it.providerId == PROVIDER_ID }?.displayName,
            profile = result.additionalUserInfo?.profile,
        )
        Log.i(TAG, "apple: signed in, isNewUser=$isNew, nameShared=${name.isNotEmpty()}")
        return AppleSignInResult(user.uid, isNew, name)
    }

    companion object {
        const val PROVIDER_ID = "apple.com"
        private const val CANCELED = "ERROR_WEB_CONTEXT_CANCELED"
        private const val TAG = "ATXF"
    }
}

/**
 * The name a new Apple account starts with. Apple shares it only on the very first sign-in;
 * iOS builds it from `fullName.givenName + familyName`. Through Firebase's web flow it can land
 * in the Auth display name, the Apple provider entry, or the sign-in's profile map, so every
 * place is checked. Never an email — a real one or a private-relay address — as a name.
 */
object AppleNames {
    fun fromSignIn(firebaseDisplayName: String?, providerDisplayName: String?, profile: Map<String, Any?>?): String {
        val fromProfile = profile?.let { p ->
            listOf(
                nameOf(p["name"]),
                join(p["firstName"], p["lastName"]),
                join(p["given_name"], p["family_name"]),
                p["fullName"] as? String,
                p["displayName"] as? String,
            )
        }.orEmpty()
        return GoogleNames.initialDisplayName(firebaseDisplayName, providerDisplayName, *fromProfile.toTypedArray())
    }

    /** Apple's `name` is either a string or `{ firstName, lastName }`. */
    private fun nameOf(value: Any?): String? = when (value) {
        is String -> value
        is Map<*, *> -> join(value["firstName"], value["lastName"])
        else -> null
    }

    private fun join(first: Any?, last: Any?): String? =
        listOfNotNull(first as? String, last as? String).joinToString(" ").trim().takeIf { it.isNotEmpty() }
}
