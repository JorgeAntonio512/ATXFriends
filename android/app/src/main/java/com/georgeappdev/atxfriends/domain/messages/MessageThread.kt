package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.Plan
import java.time.Instant

/** One conversation row: a mutual match plus what the list shows about it (MessageModels.swift). */
data class MessageThread(
    /** The match ID. */
    val id: String,
    val match: Match,
    val otherUserID: String,
    val otherUserName: String,
    val otherUserPhotoURL: String?,
    val lastMessage: Message?,
    /** Next confirmed upcoming plan (a pending reschedule still counts), if any. */
    val upcomingPlan: Plan?,
) {
    /** The newer of the last message and the last update to the upcoming plan. Drives the list order. */
    val lastActivityDate: Instant
        get() = maxOf(lastMessage?.sentAt ?: Instant.MIN, upcomingPlan?.updatedAt ?: Instant.MIN)

    companion object {
        /** The soonest upcoming plan among a match's plans (MessagingViewModel.loadMessageThreads). */
        fun upcomingPlan(plans: List<Plan>, now: Instant): Plan? =
            plans.filter { it.isUpcoming(now) }.minByOrNull { it.standingDate ?: Instant.MAX }
    }
}

/**
 * The order iOS actually renders. MessagingViewModel sorts plan-first, then MessagesListView
 * re-sorts that result purely by [MessageThread.lastActivityDate], newest first. The second sort
 * is what shows; the first only decides ties (both sorts are stable).
 */
fun sortThreadsForDisplay(threads: List<MessageThread>): List<MessageThread> =
    threads.sortedWith(viewModelOrder).sortedByDescending { it.lastActivityDate }

/**
 * MessagingViewModel's own order: threads with a confirmed plan first (soonest first), then the
 * rest by most recent message, or match creation when there are no messages.
 */
private val viewModelOrder = Comparator<MessageThread> { t1, t2 ->
    val d1 = t1.upcomingPlan?.confirmedDate
    val d2 = t2.upcomingPlan?.confirmedDate
    when {
        d1 != null && d2 != null -> d1.compareTo(d2)
        d1 != null -> -1
        d2 != null -> 1
        else -> {
            val r1 = t1.lastMessage?.sentAt ?: t1.match.createdAt
            val r2 = t2.lastMessage?.sentAt ?: t2.match.createdAt
            r2.compareTo(r1)
        }
    }
}
