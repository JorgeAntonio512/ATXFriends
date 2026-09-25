package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.NOON
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.dates
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** Ports of the iOS date formatters used by Messages. "Now" is Thu Sep 24 2026, noon, Austin. */
class MessageDatesTest {

    private val d = dates()
    private fun inMinutes(m: Long) = NOON.plus(Duration.ofMinutes(m))
    private fun inSeconds(s: Long) = NOON.plusSeconds(s)

    // Countdown (PinnedPlanCard.countdownSuffix)

    @Test
    fun countdown_minutes_underAnHour() {
        assertEquals("starts in 45 min", d.countdown(inMinutes(45)))
        assertEquals("starts in 59 min", d.countdown(inMinutes(59)))
    }

    @Test
    fun countdown_neverSaysZeroMinutes() {
        assertEquals("starts in 1 min", d.countdown(NOON))
        assertEquals("starts in 1 min", d.countdown(inSeconds(20)))
    }

    @Test
    fun countdown_minutesRoundToNearest() {
        assertEquals("starts in 10 min", d.countdown(inSeconds(10 * 60 + 29)))
        assertEquals("starts in 11 min", d.countdown(inSeconds(10 * 60 + 30)))
    }

    @Test
    fun countdown_hours_fromAnHourToThree() {
        // 59.5 minutes rounds to 60, so it switches to hours, as on iOS.
        assertEquals("starts in 1 hr", d.countdown(inSeconds(59 * 60 + 30)))
        assertEquals("starts in 1 hr", d.countdown(inMinutes(60)))
        assertEquals("starts in 2 hr", d.countdown(inMinutes(90)))
        assertEquals("starts in 3 hr", d.countdown(inMinutes(180)))
    }

    @Test
    fun countdown_nothingMoreThanThreeHoursOut() {
        assertNull(d.countdown(inSeconds(3 * 3600 + 1)))
    }

    @Test
    fun countdown_happeningNow_forTwoHoursAfterStart() {
        assertEquals("happening now", d.countdown(inSeconds(-1)))
        assertEquals("happening now", d.countdown(inMinutes(-120)))
        assertNull(d.countdown(inSeconds(-2 * 3600 - 1)))
    }

    // Pinned card headline / subline

    @Test
    fun pinnedHeadline_usesPlanDayWord() {
        assertEquals("Tonight · 7:00 PM", d.pinnedHeadline(plan(confirmedDate = at(hour = 19))))
        assertEquals("Today · 3:30 PM", d.pinnedHeadline(plan(confirmedDate = at(hour = 15, minute = 30))))
        assertEquals("Tomorrow · 9:00 AM", d.pinnedHeadline(plan(confirmedDate = at(day = 25, hour = 9))))
        assertEquals("Sat, Oct 3 · 7:00 PM", d.pinnedHeadline(plan(confirmedDate = at(month = 10, day = 3, hour = 19))))
    }

    @Test
    fun pinnedSubline_addsCountdownWhenClose() {
        assertEquals("Poker · starts in 30 min", d.pinnedSubline(plan(confirmedDate = inMinutes(30))))
        assertEquals("Poker", d.pinnedSubline(plan(confirmedDate = at(day = 26))))
    }

    @Test
    fun pinned_legacyCounterPlan_usesProposedDate() {
        val legacy = plan(status = PlanStatus.COUNTER_PROPOSED, confirmedDate = null, proposedDate = inMinutes(20))
        assertEquals("Poker · starts in 20 min", d.pinnedSubline(legacy))
    }

    // Row plan pill (MessageThread.planPillText)

    @Test
    fun planPill() {
        assertEquals("Poker · Today 7pm", d.planPill(plan(confirmedDate = at(hour = 19))))
        assertEquals("Poker · Tmrw 7:30pm", d.planPill(plan(confirmedDate = at(day = 25, hour = 19, minute = 30))))
        assertEquals("Poker · Sat 10am", d.planPill(plan(confirmedDate = at(day = 26, hour = 10))))
        assertEquals("Poker · Sat 12pm", d.planPill(plan(confirmedDate = at(day = 26, hour = 12))))
        assertNull(d.planPill(plan(confirmedDate = null)))
    }

    // Row timestamp (MessageThread.timeText)

    @Test
    fun rowTime() {
        assertEquals("9:05 AM", d.rowTime(at(hour = 9, minute = 5)))
        assertEquals("Yesterday", d.rowTime(at(day = 23, hour = 23)))
        assertEquals("Monday", d.rowTime(at(day = 21, hour = 8)))
        assertEquals("9/17/26", d.rowTime(at(day = 17, hour = 8)))
    }

    // Other plan text

    @Test
    fun proposalDate() {
        assertEquals("Today at 7pm", d.proposalDate(at(hour = 19)))
        assertEquals("Tomorrow at 7:30pm", d.proposalDate(at(day = 25, hour = 19, minute = 30)))
        assertEquals("Sat, Oct 3 at 7pm", d.proposalDate(at(month = 10, day = 3, hour = 19)))
    }

    @Test
    fun confirmedEmptyTitle() {
        assertEquals("You're on for tonight.", d.confirmedEmptyTitle(at(hour = 18)))
        assertEquals("You're on for today.", d.confirmedEmptyTitle(at(hour = 16)))
        assertEquals("You're on for tomorrow.", d.confirmedEmptyTitle(at(day = 25)))
        assertEquals("You're on for Sat, Oct 3.", d.confirmedEmptyTitle(at(month = 10, day = 3)))
    }

    @Test
    fun rescheduleSuggestion_namesWhoAsked() {
        val counter = plan(status = PlanStatus.COUNTER_PROPOSED, counterDate = at(day = 26, hour = 15), counterProposedBy = "sam")
        assertEquals("Sam suggested Saturday, ${d.shortTime(at(day = 26, hour = 15))}.", d.rescheduleSuggestion(counter, "me", "Sam"))
        assertEquals(
            "You suggested Saturday, ${d.shortTime(at(day = 26, hour = 15))}.",
            d.rescheduleSuggestion(counter.copy(counterProposedBy = "me"), "me", "Sam"),
        )
        assertEquals(
            "A new time was suggested: a new time.",
            d.rescheduleSuggestion(counter.copy(counterProposedBy = null, counterProposedDates = null), "me", "Sam"),
        )
    }

    @Test
    fun collapsedSubtitle() {
        assertEquals("Poker · Mozart's", plan(locationName = "Mozart's", location = "3825 Lake Austin Blvd").collapsedSubtitle())
        assertEquals("Poker · Zilker", plan(location = "Zilker").collapsedSubtitle())
        assertEquals("Poker · New time suggested", plan(status = PlanStatus.COUNTER_PROPOSED, location = "Zilker").collapsedSubtitle())
        assertEquals("Poker", plan().collapsedSubtitle())
    }
}
