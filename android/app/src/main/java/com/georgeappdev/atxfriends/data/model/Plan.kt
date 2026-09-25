package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.PlanFields
import java.time.Instant

/** `plans/{planID}` — the live 1-on-1 plan proposal shown in Messages threads. */
data class Plan(
    val id: String,
    val matchID: String,
    val proposerID: String,
    val receiverID: String,
    val activity: Activity,
    /** Free text read verbatim by the calendar integrations — never rename. */
    val location: String?,
    val locationName: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    /** In practice always exactly one date. */
    val proposedDates: List<Instant>,
    val status: PlanStatus,
    val confirmedDate: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val counterProposedDates: List<Instant>?,
    val counterProposedBy: String?,
    val isViewed: Boolean,
) {
    companion object {
        /**
         * Same required fields as iOS `firestoreDataToPlan`, except an unrecognized `status`
         * string decodes to [PlanStatus.UNKNOWN] instead of dropping the plan.
         */
        fun fromFirestore(id: String, data: Map<String, Any?>): Plan? {
            val r = DocReader(data)
            return Plan(
                id = id,
                matchID = r.string(PlanFields.MATCH_ID) ?: return null,
                proposerID = r.string(PlanFields.PROPOSER_ID) ?: return null,
                receiverID = r.string(PlanFields.RECEIVER_ID) ?: return null,
                activity = Activity.fromFirestore(r.map(PlanFields.ACTIVITY)) ?: return null,
                proposedDates = r.instantList(PlanFields.PROPOSED_DATES) ?: return null,
                status = PlanStatus.fromRaw(r.string(PlanFields.STATUS) ?: return null),
                createdAt = r.instant(PlanFields.CREATED_AT) ?: return null,
                updatedAt = r.instant(PlanFields.UPDATED_AT) ?: return null,
                location = r.string(PlanFields.LOCATION),
                locationName = r.string(PlanFields.LOCATION_NAME),
                locationLatitude = r.double(PlanFields.LOCATION_LATITUDE),
                locationLongitude = r.double(PlanFields.LOCATION_LONGITUDE),
                confirmedDate = r.instant(PlanFields.CONFIRMED_DATE),
                counterProposedDates = r.instantList(PlanFields.COUNTER_PROPOSED_DATES),
                counterProposedBy = r.string(PlanFields.COUNTER_PROPOSED_BY),
                // Read the same way as iOS UnreadState: missing means not yet viewed.
                isViewed = r.bool(PlanFields.IS_VIEWED) ?: false,
            )
        }
    }
}
