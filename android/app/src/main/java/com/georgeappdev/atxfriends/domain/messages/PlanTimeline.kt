package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import java.time.Instant

// Ports of the reschedule helpers on iOS `Plan` (Plan.swift). A plan with a pending
// reschedule request (status "counter") is still on at its original time.

/** Confirmed, or confirmed with a reschedule request pending. */
val Plan.isConfirmedOrReschedulePending: Boolean
    get() = status == PlanStatus.CONFIRMED || status == PlanStatus.COUNTER_PROPOSED

/** The suggested new time while a reschedule request is pending; null otherwise. */
val Plan.pendingRescheduleDate: Instant?
    get() = if (status == PlanStatus.COUNTER_PROPOSED) counterProposedDates?.firstOrNull() else null

/**
 * The time the plan currently stands at: confirmedDate, falling back to the original proposed
 * date for legacy counter plans that never had one.
 */
val Plan.standingDate: Instant?
    get() = confirmedDate ?: if (status == PlanStatus.COUNTER_PROPOSED) proposedDates.firstOrNull() else null

/** Still belongs in "upcoming" lists: its standing time or its suggested new time is ahead. */
fun Plan.isUpcoming(now: Instant): Boolean {
    if (!isConfirmedOrReschedulePending) return false
    standingDate?.let { if (it > now) return true }
    pendingRescheduleDate?.let { if (it > now) return true }
    return false
}

/**
 * Whether [userID] may accept or decline the pending reschedule. Only the non-requester can;
 * legacy requests with no counterProposedBy can be answered by either participant.
 */
fun Plan.canRespondToReschedule(userID: String): Boolean {
    if (status != PlanStatus.COUNTER_PROPOSED) return false
    if (userID != proposerID && userID != receiverID) return false
    val requester = counterProposedBy ?: return true
    return requester != userID
}

/**
 * What the thread's plans listener delivers (PlansService.listenToConfirmedPlan): upcoming
 * confirmed plans soonest first, and the IDs of every confirmed plan including past ones.
 */
data class ConfirmedPlans(val upcoming: List<Plan>, val allIDs: Set<String>) {
    companion object {
        val EMPTY = ConfirmedPlans(emptyList(), emptySet())

        fun from(plans: List<Plan>, now: Instant): ConfirmedPlans {
            val confirmed = plans.filter { it.isConfirmedOrReschedulePending }
            return ConfirmedPlans(
                upcoming = confirmed.filter { it.isUpcoming(now) }.sortedBy { it.standingDate ?: Instant.MAX },
                allIDs = confirmed.mapTo(mutableSetOf()) { it.id },
            )
        }
    }
}
