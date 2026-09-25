package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.GroupPlanFields
import java.time.Instant

/** `groupPlans/{planID}` — the multi-invitee plan shown on the Upcoming tab. */
data class GroupPlan(
    val id: String,
    val hostID: String,
    val inviteeIDs: List<String>,
    /** Keyed by invitee user ID. */
    val responses: Map<String, GroupPlanResponse>,
    val activity: Activity,
    val location: String?,
    val locationName: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val date: Instant,
    val status: GroupPlanStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * Same required fields as iOS `GroupPlansService`, except unrecognized `status` or
         * response strings decode to UNKNOWN instead of being dropped.
         */
        fun fromFirestore(id: String, data: Map<String, Any?>): GroupPlan? {
            val r = DocReader(data)
            val responses = r.map(GroupPlanFields.RESPONSES) ?: return null
            return GroupPlan(
                id = id,
                hostID = r.string(GroupPlanFields.HOST_ID) ?: return null,
                inviteeIDs = r.stringList(GroupPlanFields.INVITEE_IDS) ?: return null,
                responses = responses.mapValues { (_, v) -> GroupPlanResponse.fromRaw(v as? String) },
                activity = Activity.fromFirestore(r.map(GroupPlanFields.ACTIVITY)) ?: return null,
                date = r.instant(GroupPlanFields.DATE) ?: return null,
                status = GroupPlanStatus.fromRaw(r.string(GroupPlanFields.STATUS) ?: return null),
                createdAt = r.instant(GroupPlanFields.CREATED_AT) ?: return null,
                updatedAt = r.instant(GroupPlanFields.UPDATED_AT) ?: return null,
                location = r.string(GroupPlanFields.LOCATION),
                locationName = r.string(GroupPlanFields.LOCATION_NAME),
                locationLatitude = r.double(GroupPlanFields.LOCATION_LATITUDE),
                locationLongitude = r.double(GroupPlanFields.LOCATION_LONGITUDE),
            )
        }
    }
}
