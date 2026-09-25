package com.georgeappdev.atxfriends.data.repository

import android.util.Log
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.NotificationPreferenceFields
import com.georgeappdev.atxfriends.data.firestore.UserFields
import com.georgeappdev.atxfriends.data.firestore.WaitlistFields
import com.georgeappdev.atxfriends.data.firestore.addDocument
import com.georgeappdev.atxfriends.data.firestore.createDocument
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.domain.location.CoarseLocation
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.domain.profile.NameRules
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.Instant

/** Result of signing in to Firebase with a Google ID token. */
data class GoogleSignInResult(val uid: String, val isNewUser: Boolean, val firebaseDisplayName: String?)

/**
 * Everything that creates (or abandons) an account: iOS FirebaseAuthService's sign-up,
 * Google sign-in and deleteAccount, FirestoreService.createUser for the first `users/{uid}`
 * doc, and WaitlistView's `waitlistSignups` write.
 */
class SignupRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) {

    /** iOS `Auth.auth().createUser(withEmail:password:)`. Returns the new uid; throws a Firebase exception. */
    suspend fun createEmailAccount(email: String, password: String): String =
        requireNotNull(auth.createUserWithEmailAndPassword(email, password).await().user) { "No user after sign-up" }.uid

    /**
     * iOS `createUser` → `setData` on `users/{uid}`. Android only ever *creates* this doc: in one
     * transaction it reads it from the server and writes only if it's missing (returns false and
     * writes nothing if it exists), so an existing profile can never be replaced.
     */
    suspend fun createUserDocIfMissing(uid: String, doc: NewDocument): Boolean = withWriteTimeout {
        val ref = db.collection(Collections.USERS).document(uid)
        val created = db.runTransaction { tx ->
            if (tx.get(ref).exists()) false
            else {
                tx.createDocument(ref, doc)
                true
            }
        }.await()
        if (created) Log.i(TAG, "created users/$uid") else Log.w(TAG, "users/$uid already exists — not creating it again")
        created
    }

    /**
     * iOS `FirebaseAuthService.deleteAccount()`: deletes the signed-in Firebase Auth identity of a
     * sign-up that never got a `users` doc. If that fails for any reason (offline, or a sign-in
     * too old to delete), this signs out instead — as iOS still exits to the welcome screen — so
     * nobody is stuck. A leftover account comes back to the location gate if it signs in again.
     */
    suspend fun abandonPendingAccount() {
        val user = auth.currentUser ?: return
        try {
            withWriteTimeout { user.delete().await() }
            Log.i(TAG, "deleted pending auth account ${user.uid}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "couldn't delete ${user.uid} — signing out instead", e)
            auth.signOut()
        }
    }

    /** iOS `signInWithGoogle(credential:)`: signs in to Firebase and reports `isNewUser`. */
    suspend fun signInWithGoogle(idToken: String): GoogleSignInResult {
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        val user = requireNotNull(result.user) { "No user after Google sign-in" }
        return GoogleSignInResult(user.uid, result.additionalUserInfo?.isNewUser ?: false, user.displayName)
    }

    /** WaitlistView.submit — no Auth account is involved. */
    suspend fun joinWaitlist(email: String) {
        withWriteTimeout { db.collection(Collections.WAITLIST_SIGNUPS).addDocument(SignupWrites.waitlist(email)) }
    }

    private companion object {
        const val TAG = "ATXF"
    }
}

/**
 * Firestore/Auth writes only finish when the server confirms, so offline they'd spin forever.
 * Gives up after [ms] with an IOException (a normal, visible failure — not a cancellation).
 */
suspend fun <T : Any> withWriteTimeout(ms: Long = WRITE_TIMEOUT_MS, block: suspend () -> T): T =
    withTimeoutOrNull(ms) { block() } ?: throw IOException("Timed out after $ms ms")

const val WRITE_TIMEOUT_MS = 20_000L

/** The exact documents and fields the iOS sign-up path writes. */
object SignupWrites {

    /**
     * The first `users/{uid}` doc, as AuthViewModel.signUp / createNewSSOUser build it:
     * `FirebaseUser(displayName, photoURLs: [], activities: [], daySlotCombos: [], lat, lng,
     * radiusMiles: 10.0, isProfileComplete: false)` run through `userToFirestoreData` —
     * every other field at the model's default. The coordinate is snapped to the coarse grid
     * first, as iOS does on every write of this field. No `locationUpdatedAt` (nil on iOS).
     */
    fun newUser(displayName: String, coordinate: Coordinate, now: Instant): NewDocument {
        val snapped = CoarseLocation.snap(coordinate)
        val prefs = NewDocument.Builder()
            .put(NotificationPreferenceFields.NEW_MATCHES, true)
            .put(NotificationPreferenceFields.NEW_MESSAGES, true)
            .put(NotificationPreferenceFields.PLAN_REQUESTS, true)
            .put(NotificationPreferenceFields.PLAN_CONFIRMATIONS, true)
            .put(NotificationPreferenceFields.GROUP_UPDATES, true)
            .build()
        return NewDocument.Builder()
            .put(UserFields.DISPLAY_NAME, displayName)
            .put(UserFields.BIO, "")
            .putStrings(UserFields.PHOTO_URLS, emptyList())
            .putStrings(UserFields.ACTIVITY_IDS, emptyList())
            .putStrings(UserFields.ACTIVITY_NAMES, emptyList())
            .putBooleans(UserFields.ACTIVITY_IS_PRIMARY, emptyList())
            .putStrings(UserFields.DAY_SLOT_COMBOS, emptyList())
            .put(UserFields.LOCATION, GeoPoint(snapped.latitude, snapped.longitude))
            .put(UserFields.LATITUDE, snapped.latitude)
            .put(UserFields.LONGITUDE, snapped.longitude)
            .put(UserFields.RADIUS_MILES, DEFAULT_RADIUS_MILES)
            .put(UserFields.CREATED_AT, now)
            .put(UserFields.UPDATED_AT, now)
            .put(UserFields.IS_PROFILE_COMPLETE, false)
            .putMap(UserFields.NOTIFICATION_PREFERENCES, prefs)
            .putStrings(UserFields.BLOCKED_USERS, emptyList())
            .put(UserFields.LOCATION_SHARING_MODE, LocationSharingMode.OFF)
            .build()
    }

    /**
     * ProfileSetupFlowView's "Enter ATX Friends" (ProfileViewModel.saveProfileCriticalData +
     * uploadPhotosInBackground). iOS merge-writes the whole profile; every field it writes that
     * isn't listed here still holds exactly the value the sign-up doc gave it, so Android writes
     * only what setup changed. Name trimmed of spaces as iOS does; bio as typed.
     */
    fun completeProfile(
        displayName: String,
        bio: String,
        photoURLs: List<String>,
        activities: List<ProfileActivity>,
        daySlotCombos: List<DaySlotCombo>,
        now: Instant,
    ): DocumentUpdate = DocumentUpdate.Builder()
        .put(UserFields.DISPLAY_NAME, NameRules.trimmed(displayName))
        .put(UserFields.BIO, bio)
        .putStrings(UserFields.PHOTO_URLS, photoURLs)
        .putStrings(UserFields.ACTIVITY_IDS, activities.map { it.id })
        .putStrings(UserFields.ACTIVITY_NAMES, activities.map { it.name })
        .putBooleans(UserFields.ACTIVITY_IS_PRIMARY, activities.map { it.isPrimary })
        .putStrings(UserFields.DAY_SLOT_COMBOS, daySlotCombos.map { it.raw })
        .put(UserFields.UPDATED_AT, now)
        .put(UserFields.IS_PROFILE_COMPLETE, true)
        .build()

    /** `{ email: lowercased + trimmed, submittedAt: serverTimestamp() }` — exactly two keys. */
    fun waitlist(email: String): NewDocument = NewDocument.Builder()
        .put(WaitlistFields.EMAIL, email.lowercase().trim())
        .putServerTimestamp(WaitlistFields.SUBMITTED_AT)
        .build()

    const val DEFAULT_RADIUS_MILES = 10.0
}

/**
 * The name a new Google account starts with. iOS uses the account's *email address*
 * (`authService.currentUserEmail`) — a known bug that shows people's emails as their name.
 * Android uses the Google profile name and never anything that looks like an email; with no
 * name the Name step simply starts empty.
 */
object GoogleNames {
    fun initialDisplayName(vararg candidates: String?): String =
        candidates.firstNotNullOfOrNull { c -> c?.trim()?.takeIf { it.isNotEmpty() && '@' !in it } } ?: ""
}
