package com.georgeappdev.atxfriends.push

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.UserFields
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.Instant

internal const val TAG = "ATXF"

/**
 * The `users/{uid}` token writes, exactly as iOS FirestoreService makes them: `fcmTokens` is an
 * array changed only with arrayUnion / arrayRemove (never overwritten), so other devices'
 * tokens are never touched.
 */
object PushWrites {
    /** `addFCMToken`: the token plus `fcmTokenUpdatedAt`. */
    fun addToken(token: String, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .arrayUnion(UserFields.FCM_TOKENS, token)
        .put(UserFields.FCM_TOKEN_UPDATED_AT, now)
        .build()

    /** `removeFCMToken`: this device's token only. */
    fun removeToken(token: String): DocumentUpdate = DocumentUpdate.Builder()
        .arrayRemove(UserFields.FCM_TOKENS, token)
        .build()

    /** `migrateLegacyFCMTokenIfNeeded`: fold the old single field into the array, then drop it. */
    fun migrateLegacy(legacyToken: String): DocumentUpdate = DocumentUpdate.Builder()
        .arrayUnion(UserFields.FCM_TOKENS, legacyToken)
        .delete(UserFields.FCM_TOKEN)
        .delete(UserFields.FCM_TOKEN_UPDATED_AT)
        .build()
}

/** Where tokens are stored (a seam for tests). */
interface TokenStore {
    suspend fun update(uid: String, update: DocumentUpdate)

    /** The legacy `fcmToken` field, if the user doc still has one. */
    suspend fun legacyToken(uid: String): String?
}

class FirestoreTokenStore(private val db: FirebaseFirestore) : TokenStore {
    override suspend fun update(uid: String, update: DocumentUpdate) =
        db.collection(Collections.USERS).document(uid).applyUpdate(update)

    override suspend fun legacyToken(uid: String): String? =
        db.collection(Collections.USERS).document(uid).get().await()
            .getString(UserFields.FCM_TOKEN)?.takeIf { it.isNotEmpty() }
}

/** This device's FCM token (a seam for tests). */
interface DeviceToken {
    suspend fun current(): String
    suspend fun delete()
}

class FirebaseDeviceToken : DeviceToken {
    override suspend fun current(): String = FirebaseMessaging.getInstance().token.await()
    override suspend fun delete() {
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}

/** Remembers, across launches, that this device's token still has to be deleted. */
interface PendingTokenDelete {
    var pending: Boolean
}

class PrefsPendingTokenDelete(context: Context) : PendingTokenDelete {
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)
    override var pending: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    private companion object {
        const val KEY = "deleteTokenPending"
    }
}

/**
 * Registers this device for pushes while someone is signed in (iOS NotificationManager +
 * Avenue3App.handleAuthStateChange), and unregisters it on sign-out.
 *
 * One deliberate difference: iOS removes the token *after* Firebase has signed out, so the
 * owner-only users rule rejects the removal and the phone keeps getting that user's pushes.
 * Android removes it *before* signing out. If that removal fails (offline, or the account was
 * just deleted), the token itself is deleted so FCM stops delivering to it and the server prunes
 * it as dead; if even that fails, it's retried on the next sign-in before a new token is stored.
 */
class PushTokens(
    private val store: TokenStore,
    private val device: DeviceToken,
    private val pendingDelete: PendingTokenDelete,
    private val clock: () -> Instant = Instant::now,
) {
    /** On every sign-in, including app launch with a saved session. Never throws. */
    suspend fun onSignedIn(uid: String) {
        Log.i(TAG, "push: signed in — registering this device")
        retryPendingDelete()
        attempt("store token") { store.update(uid, PushWrites.addToken(device.current(), clock())) }
        attempt("migrate legacy token") {
            store.legacyToken(uid)?.let {
                store.update(uid, PushWrites.migrateLegacy(it))
                Log.i(TAG, "push: migrated legacy fcmToken")
            }
        }
    }

    /** FCM issued a new token (`didReceiveRegistrationToken`). Stored only while signed in. */
    suspend fun onNewToken(uid: String?, token: String) {
        if (uid == null) {
            Log.i(TAG, "push: new token while signed out — not stored")
            return
        }
        attempt("store refreshed token") { store.update(uid, PushWrites.addToken(token, clock())) }
    }

    /** Right before Firebase sign-out (and after account deletion). Never throws. */
    suspend fun beforeSignOut(uid: String) {
        val removed = attempt("remove token") {
            withTimeout(REMOVE_TIMEOUT_MS) { store.update(uid, PushWrites.removeToken(device.current())) }
        }
        if (removed) return
        pendingDelete.pending = true
        retryPendingDelete()
    }

    private suspend fun retryPendingDelete() {
        if (!pendingDelete.pending) return
        if (attempt("delete token") { withTimeout(REMOVE_TIMEOUT_MS) { device.delete() } }) {
            pendingDelete.pending = false
        }
    }

    private suspend fun attempt(what: String, block: suspend () -> Unit): Boolean = try {
        block()
        Log.i(TAG, "push: $what — ok")
        true
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "push: $what — timed out")
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "push: $what — failed", e)
        false
    }

    companion object {
        /** Signing out waits at most this long for the token removal. */
        const val REMOVE_TIMEOUT_MS = 4_000L
    }
}
