package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.firestore.Collections
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.firestore.PlanFields
import com.georgeappdev.atxfriends.data.firestore.applyUpdate
import com.georgeappdev.atxfriends.data.firestore.createDocument
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.pendingRescheduleDate
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** What an open thread needs from `plans` (a seam for tests). */
interface PlanStore {
    fun plansFor(matchID: String): Flow<List<Plan>>

    /** Applies one of the [PlanUpdates] changes. Throws on failure. */
    suspend fun update(planID: String, update: DocumentUpdate)
}

/**
 * Access to the 1-on-1 `plans` collection (PlansService.swift), always queried by matchID.
 * Plans whose status Android doesn't recognize are dropped, because iOS fails to decode them
 * and never shows them either. New plans are created only by [propose]; existing plans change
 * only through [PlanUpdates].
 */
class PlanRepository(private val db: FirebaseFirestore) : PlanStore {

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
    override fun plansFor(matchID: String): Flow<List<Plan>> = callbackFlow {
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

    override suspend fun update(planID: String, update: DocumentUpdate) {
        plans.document(planID).applyUpdate(update)
    }

    /**
     * iOS MessagingViewModel.proposePlan: the pending plan plus the planProposal message that
     * points at it ([message] gets the new plan's ID). iOS writes them one after the other;
     * here they commit as one batch, so a failed send never leaves a plan with no message and
     * retrying can't create a duplicate plan. Returns the plan ID. Throws on failure.
     */
    suspend fun propose(plan: NewDocument, message: (planID: String) -> NewDocument): String {
        val planRef = plans.document()
        db.batch()
            .createDocument(planRef, plan)
            .createDocument(db.collection(Collections.MESSAGES).document(), message(planRef.id))
            .commit().await()
        return planRef.id
    }

    private fun decode(doc: DocumentSnapshot): Plan? =
        doc.data?.let { Plan.fromFirestore(doc.id, it) }?.takeIf { it.status != PlanStatus.UNKNOWN }
}

/**
 * Every change Messages makes to an existing plan (new plans: [PlanWrites]), field for field what PlansService.swift
 * writes (spec §5.5). `updatedAt` is the device clock, as on iOS. The Firestore rules check the
 * reschedule shapes, so these must not drift.
 */
object PlanUpdates {

    /**
     * Receiver accepts a pending proposal (`confirmPlan(planID:, selectedDate: proposedDates.first)`).
     * Null when the plan has no proposed date — iOS does nothing then.
     */
    fun accept(plan: Plan, now: Instant): DocumentUpdate? {
        val date = plan.proposedDates.firstOrNull() ?: return null
        return DocumentUpdate.Builder()
            .put(PlanFields.STATUS, PlanStatus.CONFIRMED)
            .put(PlanFields.CONFIRMED_DATE, date)
            .put(PlanFields.UPDATED_AT, now)
            .build()
    }

    /** Receiver declines a pending proposal (`declinePlan`). */
    fun decline(now: Instant): DocumentUpdate = statusChange(PlanStatus.DECLINED, now)

    /** Either person cancels a confirmed plan, with or without a pending reschedule (`cancelPlan`). */
    fun cancel(now: Instant): DocumentUpdate = statusChange(PlanStatus.CANCELLED, now)

    /**
     * Either person asks to move a confirmed plan (`counterPropose(newDates: [date])`): status
     * "counter", the one suggested time, and who asked. confirmedDate is left alone — the plan
     * stays on at its original time until the other person answers.
     */
    fun requestReschedule(newDate: Instant, requesterID: String, now: Instant): DocumentUpdate =
        DocumentUpdate.Builder()
            .put(PlanFields.STATUS, PlanStatus.COUNTER_PROPOSED)
            .putInstants(PlanFields.COUNTER_PROPOSED_DATES, listOf(newDate))
            .put(PlanFields.COUNTER_PROPOSED_BY, requesterID)
            .put(PlanFields.UPDATED_AT, now)
            .build()

    /**
     * The other person accepts the new time (`acceptReschedule`): confirmed at the suggested time,
     * request fields removed. Null when there's no suggested time — iOS does nothing then.
     */
    fun acceptReschedule(plan: Plan, now: Instant): DocumentUpdate? {
        val newDate = plan.pendingRescheduleDate ?: return null
        return DocumentUpdate.Builder()
            .put(PlanFields.STATUS, PlanStatus.CONFIRMED)
            .put(PlanFields.CONFIRMED_DATE, newDate)
            .delete(PlanFields.COUNTER_PROPOSED_DATES)
            .delete(PlanFields.COUNTER_PROPOSED_BY)
            .put(PlanFields.UPDATED_AT, now)
            .build()
    }

    /**
     * The other person declines the new time (`declineReschedule`): back to confirmed at the
     * original time, request fields removed. Never cancels. Legacy counter plans that never had a
     * confirmedDate get their original proposed time back as it.
     */
    fun declineReschedule(plan: Plan, now: Instant): DocumentUpdate =
        DocumentUpdate.Builder()
            .put(PlanFields.STATUS, PlanStatus.CONFIRMED)
            .delete(PlanFields.COUNTER_PROPOSED_DATES)
            .delete(PlanFields.COUNTER_PROPOSED_BY)
            .put(PlanFields.UPDATED_AT, now)
            .apply {
                val original = plan.proposedDates.firstOrNull()
                if (plan.confirmedDate == null && original != null) put(PlanFields.CONFIRMED_DATE, original)
            }
            .build()

    private fun statusChange(status: PlanStatus, now: Instant) =
        DocumentUpdate.Builder().put(PlanFields.STATUS, status).put(PlanFields.UPDATED_AT, now).build()
}
