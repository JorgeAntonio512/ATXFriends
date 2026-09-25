package com.georgeappdev.atxfriends.data.repository

import android.util.Log
import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.MatchFields
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.TodayPlanFields
import com.georgeappdev.atxfriends.data.firestore.addDocument
import com.georgeappdev.atxfriends.data.firestore.createDocument
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.domain.today.MatchUpsert
import com.georgeappdev.atxfriends.domain.today.PlanAlreadyClaimedException
import com.georgeappdev.atxfriends.domain.today.TodayClaimStore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/**
 * Access to `todayPlans`. Reads run the same query as iOS TodayPlanService:
 * `status == "open" && scheduledTime > now`, ordered by scheduledTime ascending
 * (composite index status ASC + scheduledTime ASC, already deployed for iOS).
 * Not bounded to a calendar day: a plan for 9am tomorrow stays in the results through midnight.
 * Writes are posting a plan ([create]) and claiming one (the [TodayClaimStore] steps).
 */
class TodayPlanRepository(private val db: FirebaseFirestore) : TodayClaimStore {

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

    /** Posts a plan built by [PlanWrites.todayPlan]; returns its new ID. Throws on failure. */
    suspend fun create(plan: NewDocument): String = db.collection(Collections.TODAY_PLANS).addDocument(plan)

    /**
     * A plan someone else claimed (or its creator deleted) stops being readable to us under the
     * todayPlans read rule, so a denied read here — typically the retry after losing a race —
     * is treated as "no longer open" and fails [check] with the friendly message. (iOS surfaces
     * the raw permission error in that case.)
     */
    override suspend fun claimInTransaction(planID: String, check: (status: String?) -> Unit, claim: DocumentUpdate) {
        val ref = db.collection(Collections.TODAY_PLANS).document(planID)
        try {
            db.runTransaction { tx ->
                val status = try {
                    tx.get(ref).getString(TodayPlanFields.STATUS)
                } catch (e: FirebaseFirestoreException) {
                    if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
                    null
                }
                check(status)
                tx.update(ref, claim.fields)
                null
            }.await()
        } catch (e: Exception) {
            // Exceptions thrown inside the transaction may come back wrapped.
            throw generateSequence<Throwable>(e) { it.cause }.filterIsInstance<PlanAlreadyClaimedException>().firstOrNull() ?: e
        }
    }

    override suspend fun findMatchID(user1ID: String, user2ID: String): String? =
        db.collection(Collections.MATCHES)
            .whereEqualTo(MatchFields.USER1_ID, user1ID)
            .whereEqualTo(MatchFields.USER2_ID, user2ID)
            .limit(1)
            .get().await()
            .documents.firstOrNull()?.id

    override suspend fun commitMatchAndPlan(match: MatchUpsert, plan: NewDocument) {
        val batch = db.batch()
        val matchRef = db.collection(Collections.MATCHES).document(match.matchID)
        when (match) {
            is MatchUpsert.Upgrade -> batch.update(matchRef, match.update.fields)
            is MatchUpsert.Create -> batch.createDocument(matchRef, match.doc)
        }
        batch.createDocument(db.collection(Collections.PLANS).document(), plan)
        batch.commit().await()
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
