package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.NotificationPreferenceFields
import com.georgeappdev.atxfriends.data.firestore.UserFields
import java.time.Instant

/**
 * `users/{uid}` — the iOS `FirebaseUser`. Named UserProfile to avoid clashing with
 * Firebase Auth's own `FirebaseUser` class.
 *
 * Deliberately has no "to map" / save-whole-object method: see DocumentUpdate.
 */
data class UserProfile(
    val id: String,
    val displayName: String,
    val bio: String,
    val photoURLs: List<String>,
    val activities: List<ProfileActivity>,
    val daySlotCombos: List<DaySlotCombo>,
    val latitude: Double,
    val longitude: Double,
    val radiusMiles: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isProfileComplete: Boolean,
    val notificationPreferences: NotificationPreferences,
    val blockedUsers: List<String>,
    val locationSharingMode: LocationSharingMode,
    val locationUpdatedAt: Instant?,
    val showUpThumbsUp: Int,
    val showUpTotal: Int,
) {
    companion object {
        /**
         * Same required fields as iOS `firestoreDataToUser`: if any is missing or mistyped this
         * returns null (iOS treats such a doc as no profile). Everything else gets iOS's default.
         */
        fun fromFirestore(id: String, data: Map<String, Any?>): UserProfile? {
            val r = DocReader(data)
            return UserProfile(
                id = id,
                displayName = r.string(UserFields.DISPLAY_NAME) ?: return null,
                photoURLs = r.stringList(UserFields.PHOTO_URLS) ?: return null,
                latitude = r.double(UserFields.LATITUDE) ?: return null,
                longitude = r.double(UserFields.LONGITUDE) ?: return null,
                radiusMiles = r.double(UserFields.RADIUS_MILES) ?: return null,
                createdAt = r.instant(UserFields.CREATED_AT) ?: return null,
                updatedAt = r.instant(UserFields.UPDATED_AT) ?: return null,
                isProfileComplete = r.bool(UserFields.IS_PROFILE_COMPLETE) ?: return null,
                bio = r.string(UserFields.BIO) ?: "",
                activities = decodeActivities(r),
                daySlotCombos = r.stringList(UserFields.DAY_SLOT_COMBOS).orEmpty().map(::DaySlotCombo),
                notificationPreferences = NotificationPreferences.fromFirestore(r.reader(UserFields.NOTIFICATION_PREFERENCES)),
                blockedUsers = r.stringList(UserFields.BLOCKED_USERS).orEmpty(),
                locationSharingMode = LocationSharingMode.fromRaw(r.string(UserFields.LOCATION_SHARING_MODE)),
                locationUpdatedAt = r.instant(UserFields.LOCATION_UPDATED_AT),
                showUpThumbsUp = r.int(UserFields.SHOW_UP_THUMBS_UP) ?: 0,
                showUpTotal = r.int(UserFields.SHOW_UP_TOTAL) ?: 0,
            )
        }

        /**
         * Rebuilds activities from the three parallel arrays, as iOS does: mismatched
         * id/name lengths yield no activities; a missing or mismatched `activityIsPrimary`
         * (legacy profiles) makes every activity Main.
         */
        private fun decodeActivities(r: DocReader): List<ProfileActivity> {
            val ids = r.stringList(UserFields.ACTIVITY_IDS) ?: return emptyList()
            val names = r.stringList(UserFields.ACTIVITY_NAMES) ?: return emptyList()
            if (ids.size != names.size) return emptyList()
            val primary = r.boolList(UserFields.ACTIVITY_IS_PRIMARY)?.takeIf { it.size == ids.size }
            return ids.indices.map { i -> ProfileActivity(ids[i], names[i], primary?.get(i) ?: true) }
        }
    }
}

/** Missing keys (or the whole map) default to on, matching iOS. */
data class NotificationPreferences(
    val newMatches: Boolean = true,
    val newMessages: Boolean = true,
    val planRequests: Boolean = true,
    val planConfirmations: Boolean = true,
    val groupUpdates: Boolean = true,
) {
    companion object {
        fun fromFirestore(r: DocReader?): NotificationPreferences {
            if (r == null) return NotificationPreferences()
            return NotificationPreferences(
                newMatches = r.bool(NotificationPreferenceFields.NEW_MATCHES) ?: true,
                newMessages = r.bool(NotificationPreferenceFields.NEW_MESSAGES) ?: true,
                planRequests = r.bool(NotificationPreferenceFields.PLAN_REQUESTS) ?: true,
                planConfirmations = r.bool(NotificationPreferenceFields.PLAN_CONFIRMATIONS) ?: true,
                groupUpdates = r.bool(NotificationPreferenceFields.GROUP_UPDATES) ?: true,
            )
        }
    }
}

/**
 * A stored `"Day_Slot"` string such as `"Saturday_Wake Up"`. The raw string is kept as-is so
 * a combo Android can't parse is never lost; [day]/[slot] are UNKNOWN in that case.
 */
data class DaySlotCombo(val raw: String) {
    private val parts = raw.split("_")
    val day: DayOfWeek = if (parts.size == 2) DayOfWeek.fromRaw(parts[0]) else DayOfWeek.UNKNOWN
    val slot: TimeSlot = if (parts.size == 2) TimeSlot.fromRaw(parts[1]) else TimeSlot.UNKNOWN
    val isKnown: Boolean get() = day != DayOfWeek.UNKNOWN && slot != TimeSlot.UNKNOWN
}
