package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.PlanFields
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Read-only access to the 1-on-1 `plans` collection (PlansService.swift), always queried by
 * matchID. Plans whose status Android doesn't recognize are dropped, because iOS fails to
 * decode them and never shows them either.
 */
class PlanRepository(private val db: FirebaseFirestore) {

    private val plans get() = db.collection(Collections.PLANS)

    /** Every plan in a match, newest first (iOS `fetchPlans(forMatch:)`). Throws on failure. */
    suspend fun fetchPlans(matchID: String): List<Plan> =
        plans.whereEqualTo(PlanFields.MATCH_ID, matchID)
            .orderBy(PlanFields.CREATED_AT, Query.Direction.DESCENDING)
            .get().await()
            .documents.mapNotNull(::decode)

    /**
     * Every plan in a match, live (the query behind iOS `listenToConfirmedPlan`). The flow fails
     * with the Firestore error if the listener errors.
     */
    fun plansFor(matchID: String): Flow<List<Plan>> = callbackFlow {
        val registration = plans.whereEqualTo(PlanFields.MATCH_ID, matchID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.let { trySend(it.documents.mapNotNull(::decode)) }
            }
        awaitClose { registration.remove() }
    }

    private fun decode(doc: DocumentSnapshot): Plan? =
        doc.data?.let { Plan.fromFirestore(doc.id, it) }?.takeIf { it.status != PlanStatus.UNKNOWN }
}
