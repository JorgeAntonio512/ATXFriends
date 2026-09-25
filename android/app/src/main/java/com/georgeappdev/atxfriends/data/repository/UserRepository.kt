package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** What's at `users/{uid}`. */
sealed interface UserDoc {
    /** No document — e.g. an account that never finished signup. */
    data object Missing : UserDoc

    /** A document exists but lacks fields iOS requires (iOS treats this as no profile). */
    data object Unreadable : UserDoc

    data class Found(val profile: UserProfile) : UserDoc
}

/**
 * Read-only access to `users`. iOS only ever reads users one time (no snapshot listeners
 * anywhere in FirestoreService), so there is no live Flow here either.
 */
class UserRepository(private val db: FirebaseFirestore) {

    /** Throws on network or permission failure. */
    suspend fun fetchUser(uid: String): UserDoc {
        val snapshot = db.collection(Collections.USERS).document(uid).get().await()
        val data = snapshot.data ?: return UserDoc.Missing
        return UserProfile.fromFirestore(uid, data)?.let(UserDoc::Found) ?: UserDoc.Unreadable
    }
}
