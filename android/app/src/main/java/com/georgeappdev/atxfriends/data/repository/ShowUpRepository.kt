package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.ShowUpReportFields
import com.georgeappdev.atxfriends.data.firestore.TodayPlanFields
import com.georgeappdev.atxfriends.data.firestore.addDocument
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.domain.messages.awaitingReport
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** What the thread's "How did it go?" card needs (a seam for tests). */
interface ShowUpStore {
    suspend fun fetchPendingReport(myID: String, otherUserID: String, now: Instant): TodayPlan?
    suspend fun submit(report: NewDocument)
}

/**
 * Show-up reports (TodayPlanService.swift). Reports go into the write-once `showUpReports`
 * queue; the applyShowUpReport Cloud Function checks them and updates the plan and the reported
 * person's counts. Clients can't read reports back.
 */
class ShowUpRepository(private val db: FirebaseFirestore) : ShowUpStore {

    /**
     * iOS `fetchPendingShowUpReport`: the claimed Today plans between the two people (two
     * equality queries, one per creator/claimer order), and the first that still needs a report
     * from [myID]. Throws on failure.
     */
    override suspend fun fetchPendingReport(myID: String, otherUserID: String, now: Instant): TodayPlan? {
        val plans = db.collection(Collections.TODAY_PLANS)
        val mineClaimed = plans.whereEqualTo(TodayPlanFields.CREATOR_ID, myID)
            .whereEqualTo(TodayPlanFields.CLAIMER_ID, otherUserID).get().await()
        val theirsClaimed = plans.whereEqualTo(TodayPlanFields.CREATOR_ID, otherUserID)
            .whereEqualTo(TodayPlanFields.CLAIMER_ID, myID).get().await()
        return (mineClaimed.documents + theirsClaimed.documents)
            .mapNotNull { doc -> doc.data?.let { TodayPlan.fromFirestore(doc.id, it) } }
            .firstOrNull { it.awaitingReport(myID, now) }
    }

    /** iOS `submitShowUpReport` (`addDocument`). Throws on failure. */
    override suspend fun submit(report: NewDocument) {
        db.collection(Collections.SHOW_UP_REPORTS).addDocument(report)
    }
}

/** The exact `showUpReports` document iOS writes. */
object ShowUpWrites {
    /** {reporterID, reportedUserID, planID, didShowUp, createdAt} — all five fields the rules require. */
    fun report(planID: String, reporterID: String, reportedUserID: String, didShowUp: Boolean, now: Instant): NewDocument =
        NewDocument.Builder()
            .put(ShowUpReportFields.REPORTER_ID, reporterID)
            .put(ShowUpReportFields.REPORTED_USER_ID, reportedUserID)
            .put(ShowUpReportFields.PLAN_ID, planID)
            .put(ShowUpReportFields.DID_SHOW_UP, didShowUp)
            .put(ShowUpReportFields.CREATED_AT, now)
            .build()
}
