package com.georgeappdev.atxfriends.domain.today

import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.domain.openslots.OpenSlot
import com.georgeappdev.atxfriends.domain.openslots.OpenSlotSource
import com.georgeappdev.atxfriends.domain.openslots.RankedOpenSlot
import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayFeedTest {

    private val zone = ZoneId.of("America/Chicago")
    private val usTime = DateTimeFormatter.ofPattern("h:mm a", Locale.US)::format

    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(2026, 9, day, hour, minute, second).atZone(zone).toInstant()

    private fun plan(id: String, activity: String, time: Instant, creator: String = "other") = TodayPlan(
        id = id, creatorID = creator,
        activity = Activity("act-$activity", activity, false, Instant.EPOCH, true),
        scheduledTime = time, note = null, location = null, locationName = null,
        locationLatitude = null, locationLongitude = null, status = TodayPlanStatus.OPEN, claimerID = null,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, creatorReportedClaimer = null, claimerReportedCreator = null,
    )

    // Time badge

    @Test
    fun timeBadgeSaysTodayTomorrowOrJustTheTime() {
        val now = at(25, 14)
        assertEquals("Today 7:00 PM", TodayFeed.timeBadgeLabel(at(25, 19), now, zone, usTime))
        assertEquals("Tomorrow 9:00 AM", TodayFeed.timeBadgeLabel(at(26, 9), now, zone, usTime))
        assertEquals("7:30 PM", TodayFeed.timeBadgeLabel(at(27, 19, 30), now, zone, usTime))
    }

    @Test
    fun tomorrowBadgeRelabelsToTodayAfterMidnight() {
        val plan = at(26, 9)
        assertEquals("Tomorrow 9:00 AM", TodayFeed.timeBadgeLabel(plan, at(25, 23, 59), zone, usTime))
        assertEquals("Today 9:00 AM", TodayFeed.timeBadgeLabel(plan, at(26, 0, 1), zone, usTime))
    }

    // Ghost-card time line

    @Test
    fun timeUntilLine() {
        val now = at(25, 16)
        assertEquals("In 3 hrs · 7pm", TodayFeed.timeUntilLine(at(25, 19), now, zone))
        assertEquals("In 1 hr · 5pm", TodayFeed.timeUntilLine(at(25, 17), now, zone))
        assertEquals("In 45 min · 4:45pm", TodayFeed.timeUntilLine(at(25, 16, 45), now, zone))
        assertEquals("In 15 min · 4:15pm", TodayFeed.timeUntilLine(at(25, 16, 15), now, zone))
        assertEquals("Now · 4pm", TodayFeed.timeUntilLine(now, now, zone))
        assertEquals("Now · 3:30pm", TodayFeed.timeUntilLine(at(25, 15, 30), now, zone))
        assertEquals("In 8 hrs · 12am", TodayFeed.timeUntilLine(at(26, 0), now, zone))
        assertEquals("In 20 hrs · 12pm", TodayFeed.timeUntilLine(at(26, 12), now, zone))
    }

    @Test
    fun timeUntilLineRoundsLikeSwift() {
        val now = at(25, 16)
        // 59.5 minutes rounds up to 60, which is no longer "< 60 min", so it reads in hours.
        assertEquals("In 1 hr · 4:59pm", TodayFeed.timeUntilLine(at(25, 16, 59, 30), now, zone))
        // 2.5 hours rounds half up to 3.
        assertEquals("In 3 hrs · 6:30pm", TodayFeed.timeUntilLine(at(25, 18, 30), now, zone))
    }

    @Test
    fun accessibleTimeDescription() {
        val now = at(25, 10)
        assertEquals("tonight at 7 PM", TodayFeed.accessibleTimeDescription(at(25, 19), now, zone, Locale.US))
        assertEquals("today at 12:30 PM", TodayFeed.accessibleTimeDescription(at(25, 12, 30), now, zone, Locale.US))
        assertEquals("tomorrow at 8 AM", TodayFeed.accessibleTimeDescription(at(26, 8), now, zone, Locale.US))
    }

    // Expiry and filtering

    @Test
    fun expiredPlansAreHidden() {
        val now = at(25, 16)
        val plans = listOf(
            plan("past", "Tacos", now.minusSeconds(1)),
            plan("now", "Trivia", now),
            plan("later", "Tacos", at(25, 19)),
        )
        // Expired means strictly before now, as on iOS.
        assertEquals(listOf("now", "later"), TodayFeed.filteredPlans(plans, null, now).map { it.id })
        assertEquals(listOf("later"), TodayFeed.filteredPlans(plans, null, now.plusMillis(1)).map { it.id })
    }

    @Test
    fun activityFilterAndChips() {
        val now = at(25, 16)
        val plans = listOf(
            plan("1", "Trivia", at(25, 18)),
            plan("2", "Tacos", at(25, 19)),
            plan("3", "Tacos", at(25, 20)),
            plan("4", "Bowling", now.minusSeconds(60)),
        )
        assertEquals(listOf("Tacos", "Trivia"), TodayFeed.availableActivities(plans, now))
        assertEquals(listOf("2", "3"), TodayFeed.filteredPlans(plans, "Tacos", now).map { it.id })
        assertEquals(emptyList<String>(), TodayFeed.filteredPlans(plans, "Bowling", now).map { it.id })
    }

    // Claimed banner

    @Test
    fun claimedDetection() {
        val now = at(25, 16)
        val mine = plan("mine", "Tacos", at(25, 19), creator = "me")
        val mineExpired = plan("old", "Trivia", now.minusSeconds(1), creator = "me")
        val theirs = plan("theirs", "Bowling", at(25, 19))

        assertEquals("mine", TodayFeed.justClaimed(listOf(mine, theirs), listOf(theirs), "me", now)?.id)
        assertNull(TodayFeed.justClaimed(listOf(mine, theirs), listOf(mine), "me", now))
        assertNull(TodayFeed.justClaimed(listOf(mineExpired), emptyList(), "me", now))
        assertNull(TodayFeed.justClaimed(listOf(mine), listOf(mine), "me", now))
    }

    // Section header

    @Test
    fun openSlotsHeader() {
        fun slot(source: OpenSlotSource) = RankedOpenSlot(OpenSlot("x", Instant.EPOCH, "Tacos", DayOfWeek.FRIDAY, TimeSlot.EVENING), source)
        val onePlan = listOf(plan("1", "Tacos", at(25, 19)))
        assertEquals(OpenSlotsHeader.OR_POST_YOUR_OWN, TodayFeed.openSlotsHeader(onePlan, listOf(slot(OpenSlotSource.FALLBACK))))
        assertEquals(OpenSlotsHeader.FREE_IN_THE_NEXT_DAY, TodayFeed.openSlotsHeader(emptyList(), listOf(slot(OpenSlotSource.FALLBACK))))
        assertEquals(
            OpenSlotsHeader.YOUR_OPEN_SLOTS,
            TodayFeed.openSlotsHeader(emptyList(), listOf(slot(OpenSlotSource.USUAL), slot(OpenSlotSource.FALLBACK))),
        )
    }

    @Test
    fun nextUsualSlotLineLowercasesTheSlot() {
        assertEquals("Your next usual slot: Saturday wake up", TodayFeed.nextUsualSlotLine("Saturday", "Wake Up"))
    }
}
