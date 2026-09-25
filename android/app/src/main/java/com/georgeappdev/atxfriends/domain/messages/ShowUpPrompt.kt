package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import java.time.Instant

// Ports of the TodayPlan helpers the thread's "How did it go?" card uses (TodayPlan.swift).

/** The other participant (creator ↔ claimer), or null if [userID] is neither. */
fun TodayPlan.otherUserID(userID: String): String? = when (userID) {
    creatorID -> claimerID
    claimerID -> creatorID
    else -> null
}

/**
 * Whether [userID] still owes a show-up report about the other person: the plan was claimed,
 * its time has passed, and this side's verdict hasn't been recorded yet.
 */
fun TodayPlan.awaitingReport(userID: String, now: Instant): Boolean {
    if (scheduledTime >= now || status != TodayPlanStatus.CLAIMED || claimerID == null) return false
    return when (userID) {
        creatorID -> creatorReportedClaimer == null
        claimerID -> claimerReportedCreator == null
        else -> false
    }
}
