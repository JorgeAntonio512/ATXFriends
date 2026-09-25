package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.TodayPlanFields
import java.time.Instant

/** `todayPlans/{planID}` — an open slot on the Today tab that someone can claim. */
data class TodayPlan(
    val id: String,
    val creatorID: String,
    val activity: Activity,
    val scheduledTime: Instant,
    val note: String?,
    val location: String?,
    val locationName: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val status: TodayPlanStatus,
    val claimerID: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Show-up verdicts, written server-side by the applyShowUpReport Cloud Function. */
    val creatorReportedClaimer: Boolean?,
    val claimerReportedCreator: Boolean?,
) {
    companion object {
        /**
         * Same required fields as iOS `TodayPlanService.decode`, except an unrecognized
         * `status` decodes to [TodayPlanStatus.UNKNOWN] instead of dropping the plan.
         */
        fun fromFirestore(id: String, data: Map<String, Any?>): TodayPlan? {
            val r = DocReader(data)
            return TodayPlan(
                id = id,
                creatorID = r.string(TodayPlanFields.CREATOR_ID) ?: return null,
                activity = Activity.fromFirestore(r.map(TodayPlanFields.ACTIVITY)) ?: return null,
                scheduledTime = r.instant(TodayPlanFields.SCHEDULED_TIME) ?: return null,
                status = TodayPlanStatus.fromRaw(r.string(TodayPlanFields.STATUS) ?: return null),
                createdAt = r.instant(TodayPlanFields.CREATED_AT) ?: return null,
                updatedAt = r.instant(TodayPlanFields.UPDATED_AT) ?: return null,
                note = r.string(TodayPlanFields.NOTE),
                location = r.string(TodayPlanFields.LOCATION),
                locationName = r.string(TodayPlanFields.LOCATION_NAME),
                locationLatitude = r.double(TodayPlanFields.LOCATION_LATITUDE),
                locationLongitude = r.double(TodayPlanFields.LOCATION_LONGITUDE),
                claimerID = r.string(TodayPlanFields.CLAIMER_ID),
                creatorReportedClaimer = r.bool(TodayPlanFields.CREATOR_REPORTED_CLAIMER),
                claimerReportedCreator = r.bool(TodayPlanFields.CLAIMER_REPORTED_CREATOR),
            )
        }
    }
}
