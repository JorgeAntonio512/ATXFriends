package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.ActivityFields
import com.georgeappdev.atxfriends.data.firestore.DocReader
import java.time.Instant

/**
 * The `Activity` map embedded in plans, todayPlans and groupPlans. iOS writes it with
 * `Firestore.Encoder()`, so the keys are the Swift property names and `createdAt` is a Timestamp.
 */
data class Activity(
    val id: String,
    val name: String,
    val isUserAdded: Boolean,
    val createdAt: Instant,
    val isPrimary: Boolean,
) {
    companion object {
        /** Null if any key iOS's Codable decoder requires is missing — iOS skips the parent doc too. */
        fun fromFirestore(map: Map<String, Any?>?): Activity? {
            val r = DocReader(map ?: return null)
            return Activity(
                id = r.string(ActivityFields.ID) ?: return null,
                name = r.string(ActivityFields.NAME) ?: return null,
                isUserAdded = r.bool(ActivityFields.IS_USER_ADDED) ?: return null,
                createdAt = r.instant(ActivityFields.CREATED_AT) ?: return null,
                isPrimary = r.bool(ActivityFields.IS_PRIMARY) ?: return null,
            )
        }
    }
}

/**
 * One of a user's chosen activities. `users/{uid}` stores these as three parallel arrays
 * (`activityIDs`, `activityNames`, `activityIsPrimary`), not as embedded Activity maps.
 */
data class ProfileActivity(
    val id: String,
    val name: String,
    val isPrimary: Boolean,
)
