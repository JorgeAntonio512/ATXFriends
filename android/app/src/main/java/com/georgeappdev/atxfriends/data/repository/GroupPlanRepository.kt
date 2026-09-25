package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.GroupPlanFields
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.addDocument
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/** What the Upcoming tab needs from `groupPlans` (a seam for tests). */
interface GroupPlanStore {
    fun groupPlansFor(uid: String): Flow<List<GroupPlan>>
    suspend fun respond(planID: String, update: DocumentUpdate)
    suspend fun cancel(planID: String, update: DocumentUpdate)
}

/**
 * Access to `groupPlans` (GroupPlansService.swift). Writes are shaped by [PlanWrites]:
 * creating an invite, an invitee's own response, and the host's cancel.
 */
class GroupPlanRepository(private val db: FirebaseFirestore) : GroupPlanStore {

    private val groupPlans get() = db.collection(Collections.GROUP_PLANS)

    /**
     * Every group plan the user hosts or is invited to, live, in any status (iOS
     * `listenToGroupPlans`: two listeners merged, host copies winning on a duplicate ID).
     * Emits once both listeners have delivered. Unlike iOS, which logs listener errors and keeps
     * showing its spinner, the flow fails with the Firestore error so the screen can say so.
     */
    override fun groupPlansFor(uid: String): Flow<List<GroupPlan>> =
        combine(
            live(groupPlans.whereArrayContains(GroupPlanFields.INVITEE_IDS, uid)),
            live(groupPlans.whereEqualTo(GroupPlanFields.HOST_ID, uid)),
        ) { invited, hosted ->
            (invited.associateBy { it.id } + hosted.associateBy { it.id }).values.toList()
        }

    /** Creates an invite built by [PlanWrites.groupPlan]; returns its new ID. Throws on failure. */
    suspend fun create(plan: NewDocument): String = groupPlans.addDocument(plan)

    override suspend fun respond(planID: String, update: DocumentUpdate) = groupPlans.document(planID).applyUpdate(update)

    override suspend fun cancel(planID: String, update: DocumentUpdate) = groupPlans.document(planID).applyUpdate(update)

    private fun live(query: Query): Flow<List<GroupPlan>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.let { trySend(decode(it)) }
        }
        awaitClose { registration.remove() }
    }

    /** Documents that don't decode are skipped, as on iOS. */
    private fun decode(snapshot: QuerySnapshot): List<GroupPlan> =
        snapshot.documents.mapNotNull { doc -> doc.data?.let { GroupPlan.fromFirestore(doc.id, it) } }
}
