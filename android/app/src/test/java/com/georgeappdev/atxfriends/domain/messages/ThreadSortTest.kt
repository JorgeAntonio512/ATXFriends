package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.thread
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list order iOS renders: MessagesListView.sortedThreads (spec §1.5, §5.8). */
class ThreadSortTest {

    private fun ids(threads: List<MessageThread>) = sortThreadsForDisplay(threads).map { it.id }

    @Test
    fun newestLastMessageFirst() {
        val threads = listOf(
            thread("old", lastMessageAt = at(day = 20)),
            thread("new", lastMessageAt = at(day = 24, hour = 9)),
            thread("mid", lastMessageAt = at(day = 22)),
        )
        assertEquals(listOf("new", "mid", "old"), ids(threads))
    }

    @Test
    fun aPlanUpdateCountsAsActivity() {
        val threads = listOf(
            thread("message", lastMessageAt = at(day = 23)),
            thread("planUpdated", lastMessageAt = at(day = 20), plan = plan(updatedAt = at(day = 24, hour = 8))),
        )
        assertEquals(listOf("planUpdated", "message"), ids(threads))
    }

    @Test
    fun anUpcomingPlanDoesNotJumpTheQueue() {
        // The view model's plan-first sort is overridden by the view's activity sort.
        val threads = listOf(
            thread("plan", lastMessageAt = at(day = 10), plan = plan(updatedAt = at(day = 10))),
            thread("chatty", lastMessageAt = at(day = 24)),
        )
        assertEquals(listOf("chatty", "plan"), ids(threads))
    }

    @Test
    fun threadsWithNoActivityGoLast_newestMatchFirst() {
        // No messages and no plan tie at "distant past"; the view model's order breaks the tie.
        val threads = listOf(
            thread("olderMatch", matchCreatedAt = at(month = 3, day = 1)),
            thread("active", lastMessageAt = at(day = 1)),
            thread("newerMatch", matchCreatedAt = at(month = 8, day = 1)),
        )
        assertEquals(listOf("active", "newerMatch", "olderMatch"), ids(threads))
    }

    @Test
    fun exactTies_fallBackToViewModelOrder_planSoonestFirst() {
        val sameTime = at(day = 20)
        val threads = listOf(
            thread("noPlan", lastMessageAt = sameTime),
            thread("laterPlan", lastMessageAt = sameTime, plan = plan(confirmedDate = at(day = 28), updatedAt = sameTime)),
            thread("soonerPlan", lastMessageAt = sameTime, plan = plan(confirmedDate = at(day = 26), updatedAt = sameTime)),
        )
        assertEquals(listOf("soonerPlan", "laterPlan", "noPlan"), ids(threads))
    }

    @Test
    fun lastActivityDate_isTheLaterOfMessageAndPlanUpdate() {
        val t = thread("t", lastMessageAt = at(day = 22), plan = plan(updatedAt = at(day = 21)))
        assertEquals(at(day = 22), t.lastActivityDate)
    }

    @Test
    fun upcomingPlan_isTheSoonestStillAhead() {
        val now = at(day = 24, hour = 12)
        val plans = listOf(
            plan(id = "past", confirmedDate = at(day = 23)),
            plan(id = "later", confirmedDate = at(day = 30)),
            plan(id = "soon", confirmedDate = at(day = 25)),
            plan(id = "pending", status = com.georgeappdev.atxfriends.data.model.PlanStatus.PENDING, confirmedDate = null),
        )
        assertEquals("soon", MessageThread.upcomingPlan(plans, now)?.id)
    }
}
