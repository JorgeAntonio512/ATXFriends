package com.georgeappdev.atxfriends.data.repository

import android.util.Log
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.TodayPlanFields
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Read-only access to `todayPlans`, running the same query as iOS TodayPlanService:
 * `status == "open" && scheduledTime > now`, ordered by scheduledTime ascending
 * (composite index status ASC + scheduledTime ASC, already deployed for iOS).
 * Not bounded to a calendar day: a plan for 9am tomorrow stays in the results through midnight.
 */
class TodayPlanRepository(private val db: FirebaseFirestore) {

    /** One-time fetch. Throws on network or permission failure. */
    suspend fun fetchOpenPlans(): List<TodayPlan> = decode(openPlansQuery(Instant.now()).get().await())

    /**
     * Realtime feed. The time bound is fixed when collection starts (as on iOS it's fixed when the
     * listener attaches), so a plan that expires later stays in results; callers filter expired plans.
     * Listener errors are logged and skipped, like iOS.
     */
    fun openPlans(): Flow<List<TodayPlan>> = callbackFlow {
        val registration = openPlansQuery(Instant.now()).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "todayPlans listener failed", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.let(::decode).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    private fun openPlansQuery(now: Instant): Query =
        db.collection(Collections.TODAY_PLANS)
            .whereEqualTo(TodayPlanFields.STATUS, TodayPlanStatus.OPEN.raw)
            .whereGreaterThan(TodayPlanFields.SCHEDULED_TIME, Timestamp(now.epochSecond, now.nano))
            .orderBy(TodayPlanFields.SCHEDULED_TIME, Query.Direction.ASCENDING)

    /** Documents that don't decode are skipped, as on iOS. */
    private fun decode(snapshot: QuerySnapshot): List<TodayPlan> =
        snapshot.documents.mapNotNull { doc -> doc.data?.let { TodayPlan.fromFirestore(doc.id, it) } }

    private companion object {
        const val TAG = "TodayPlanRepository"
    }
}
