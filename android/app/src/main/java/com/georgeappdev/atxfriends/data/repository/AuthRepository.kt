package com.georgeappdev.atxfriends.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** [displayName] is Firebase Auth's own (a Google account's name; null for email accounts). */
data class AuthUser(val uid: String, val email: String?, val displayName: String? = null)

/**
 * Firebase Auth sign-in for existing accounts. Creating (and abandoning) accounts lives in
 * SignupRepository, behind the location gate. Firebase persists the session itself, so a
 * signed-in user stays signed in across launches.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    /** The signed-in user, or null. Emits the current value immediately, then on every change. */
    val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { AuthUser(it.uid, it.email, it.displayName) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /** Throws a Firebase exception on failure; map it with AuthErrorMapper. */
    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() = auth.signOut()
}
