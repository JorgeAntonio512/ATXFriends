package com.georgeappdev.atxfriends.data.model

import com.georgeappdev.atxfriends.data.firestore.DocReader
import com.georgeappdev.atxfriends.data.firestore.ShowUpReportFields
import java.time.Instant

/**
 * `showUpReports/{auto-id}` — a write-once input queue for the applyShowUpReport Cloud
 * Function. Rules deny all client reads; the decoder exists only so the shape is pinned
 * down and tested alongside the others.
 */
data class ShowUpReport(
    val id: String,
    val reporterID: String,
    val reportedUserID: String,
    val planID: String,
    val didShowUp: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun fromFirestore(id: String, data: Map<String, Any?>): ShowUpReport? {
            val r = DocReader(data)
            return ShowUpReport(
                id = id,
                reporterID = r.string(ShowUpReportFields.REPORTER_ID) ?: return null,
                reportedUserID = r.string(ShowUpReportFields.REPORTED_USER_ID) ?: return null,
                planID = r.string(ShowUpReportFields.PLAN_ID) ?: return null,
                didShowUp = r.bool(ShowUpReportFields.DID_SHOW_UP) ?: return null,
                createdAt = r.instant(ShowUpReportFields.CREATED_AT) ?: return null,
            )
        }
    }
}
