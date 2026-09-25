package com.georgeappdev.atxfriends.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

data class AuthUser(val uid: String, val email: String?)

/**
 * Firebase Auth for existing accounts only. There is intentionally no create-account method:
 * new accounts must pass the location gate and profile setup, which Android doesn't have yet.
 * Firebase persists the session itself, so a signed-in user stays signed in across launches.
 */
class AuthRepository(private val auth: FirebaseAuth) {

    /** The signed-in user, or null. Emits the current value immediately, then on every change. */
    val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { AuthUser(it.uid, it.email) })
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
