package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NotificationPreferenceFields
import com.georgeappdev.atxfriends.data.firestore.UserFields
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.NotificationPreferences
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.domain.location.CoarseLocation
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.time.Instant

/** Changes to the signed-in user's own `users/{uid}` doc (a seam for tests). */
fun interface ProfileWriter {
    /** Throws on failure; nothing is written then. */
    suspend fun update(uid: String, update: DocumentUpdate)
}

/** Writes to `users/{uid}`, always field by field (see DocumentUpdate). */
class ProfileRepository(private val db: FirebaseFirestore) : ProfileWriter {
    override suspend fun update(uid: String, update: DocumentUpdate) {
        db.collection(Collections.USERS).document(uid).applyUpdate(update)
    }
}

/**
 * Every `users/{uid}` write Settings makes, built as field-level updates.
 *
 * iOS saves most Settings screens through `FirestoreService.updateUser`, a merge-write of the
 * whole profile (`userToFirestoreData`). Android writes only the fields the screen changed plus
 * `updatedAt`, with the same names, types and values, so it can never reset a field it didn't
 * mean to touch. The narrow iOS writes (`updateNotificationPreferences`, `updateLocationSharing`,
 * block/unblock) are mirrored exactly.
 */
object UserWrites {

    fun displayName(name: String, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .put(UserFields.DISPLAY_NAME, name)
        .put(UserFields.UPDATED_AT, now)
        .build()

    /** Stored as a Double, like iOS `radiusMiles`. */
    fun radius(miles: Int, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .put(UserFields.RADIUS_MILES, miles.toDouble())
        .put(UserFields.UPDATED_AT, now)
        .build()

    /** The three parallel arrays iOS stores activities as. */
    fun activities(list: List<ProfileActivity>, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .putStrings(UserFields.ACTIVITY_IDS, list.map { it.id })
        .putStrings(UserFields.ACTIVITY_NAMES, list.map { it.name })
        .putBooleans(UserFields.ACTIVITY_IS_PRIMARY, list.map { it.isPrimary })
        .put(UserFields.UPDATED_AT, now)
        .build()

    /** `"Day_Slot"` strings, e.g. `"Saturday_Wake Up"`, in the user's tap order. */
    fun daySlotCombos(list: List<DaySlotCombo>, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .putStrings(UserFields.DAY_SLOT_COMBOS, list.map { it.raw })
        .put(UserFields.UPDATED_AT, now)
        .build()

    fun photoURLs(urls: List<String>, now: Instant): DocumentUpdate = DocumentUpdate.Builder()
        .putStrings(UserFields.PHOTO_URLS, urls)
        .put(UserFields.UPDATED_AT, now)
        .build()

    /**
     * iOS `updateNotificationPreferences`: all five keys (including `groupUpdates`, which has no
     * toggle any more but keeps its stored value) plus `updatedAt`.
     */
    fun notificationPreferences(p: NotificationPreferences, now: Instant): DocumentUpdate {
        val map = UserFields.NOTIFICATION_PREFERENCES
        return DocumentUpdate.Builder()
            .put("$map.${NotificationPreferenceFields.NEW_MATCHES}", p.newMatches)
            .put("$map.${NotificationPreferenceFields.NEW_MESSAGES}", p.newMessages)
            .put("$map.${NotificationPreferenceFields.PLAN_REQUESTS}", p.planRequests)
            .put("$map.${NotificationPreferenceFields.PLAN_CONFIRMATIONS}", p.planConfirmations)
            .put("$map.${NotificationPreferenceFields.GROUP_UPDATES}", p.groupUpdates)
            .put(UserFields.UPDATED_AT, now)
            .build()
    }

    /**
     * iOS `updateLocationSharing`: the mode, and — only when a fresh fix is given — the coarse
     * coordinate (snapped here too, as iOS does), its GeoPoint and `locationUpdatedAt`. No
     * `updatedAt`. Turning sharing off leaves the stored coordinate alone.
     */
    fun locationSharing(mode: LocationSharingMode, fix: Coordinate?, now: Instant): DocumentUpdate {
        val builder = DocumentUpdate.Builder().put(UserFields.LOCATION_SHARING_MODE, mode)
        if (fix != null) {
            val snapped = CoarseLocation.snap(fix)
            builder
                .put(UserFields.LATITUDE, snapped.latitude)
                .put(UserFields.LONGITUDE, snapped.longitude)
                .put(UserFields.LOCATION, GeoPoint(snapped.latitude, snapped.longitude))
                .put(UserFields.LOCATION_UPDATED_AT, now)
        }
        return builder.build()
    }

    /** PrivacyAndSafetyViewModel.blockUser step 1. */
    fun block(otherUserID: String): DocumentUpdate =
        DocumentUpdate.Builder().arrayUnion(UserFields.BLOCKED_USERS, otherUserID).build()

    /** PrivacyAndSafetyViewModel.unblockUser step 1. */
    fun unblock(otherUserID: String): DocumentUpdate =
        DocumentUpdate.Builder().arrayRemove(UserFields.BLOCKED_USERS, otherUserID).build()
}
