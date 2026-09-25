package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.ProfileWriter
import com.georgeappdev.atxfriends.session.SessionManager
import java.time.Instant

/**
 * Saves a change to the signed-in user's own profile: writes [DocumentUpdate]s to
 * `users/{uid}`, and only after the write succeeds updates the in-memory profile every tab
 * reads. Settings screens edit through this, so a failed write never shows as saved.
 */
class ProfileEdits(
    private val session: SessionManager,
    private val writer: ProfileWriter,
    private val clock: () -> Instant = Instant::now,
) {
    /** The signed-in user's profile, or null when signed out. */
    val profile: UserProfile? get() = session.currentProfile

    /** Throws when signed out or when the write fails; nothing local changes then. */
    suspend fun save(update: (Instant) -> DocumentUpdate, apply: (UserProfile, Instant) -> UserProfile) {
        val before = session.currentProfile ?: throw IllegalStateException("Not signed in")
        val now = clock()
        writer.update(before.id, update(now))
        val latest = session.currentProfile?.takeIf { it.id == before.id } ?: before
        session.profileChanged(apply(latest, now))
    }
}
