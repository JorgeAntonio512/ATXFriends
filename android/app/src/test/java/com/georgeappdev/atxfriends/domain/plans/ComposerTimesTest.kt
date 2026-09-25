package com.georgeappdev.atxfriends.domain.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** ProposePlanSheet's clamped time ranges, in Austin time. */
class ComposerTimesTest {

    private val zone = ZoneId.of("America/Chicago")
    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(2026, 9, day, hour, minute, second).atZone(zone).toInstant()

    @Test
    fun nextHourRoundedUp_isTheNextFullHour_evenAcrossMidnight() {
        assertEquals(at(25, 17), ComposerTimes.nextHourRoundedUp(at(25, 16, 20), zone))
        assertEquals(at(25, 17), ComposerTimes.nextHourRoundedUp(at(25, 16, 0), zone))
        assertEquals(at(26, 0), ComposerTimes.nextHourRoundedUp(at(25, 23, 30), zone))
    }

    @Test
    fun todayRange_isFifteenMinutesOut_throughOneSecondBeforeMidnight() {
        val r = ComposerTimes.todayRange(at(25, 16, 20), zone)
        assertEquals(at(25, 16, 35), r.lower)
        assertEquals(at(25, 23, 59, 59), r.upper)
    }

    @Test
    fun tomorrowRange_isStartOfTomorrow_throughExactly24HoursFromNow() {
        val r = ComposerTimes.tomorrowRange(at(25, 16, 20), zone)
        assertEquals(at(26, 0), r.lower)
        assertEquals(at(26, 16, 20), r.upper)
    }

    @Test
    fun todayRunsOut_fifteenMinutesBeforeMidnight() {
        assertTrue(ComposerTimes.todayIsAvailable(at(25, 23, 44, 59), zone))
        assertFalse(ComposerTimes.todayIsAvailable(at(25, 23, 45, 0), zone))
        assertEquals(DayChoice.TOMORROW, ComposerTimes.defaultDayChoice(at(25, 23, 50), zone))
        // Once past, Today's range collapses to the end of the day, as on iOS.
        val r = ComposerTimes.todayRange(at(25, 23, 50), zone)
        assertEquals(r.upper, r.lower)
    }

    @Test
    fun defaultPickerTime_isNextHour_clampedIntoTheSegment() {
        val now = at(25, 16, 20)
        assertEquals(at(25, 17), ComposerTimes.defaultPickerTime(DayChoice.TODAY, now, zone))
        // 5pm today is before tomorrow's range → clamps up to midnight.
        assertEquals(at(26, 0), ComposerTimes.defaultPickerTime(DayChoice.TOMORROW, now, zone))
        // 11:50pm: next hour (00:00) is past Today's end → clamps down to 23:59:59.
        assertEquals(at(25, 23, 59, 59), ComposerTimes.defaultPickerTime(DayChoice.TODAY, at(25, 23, 10), zone))
    }

    @Test
    fun openPostTime_clampsToTheSegmentRange() {
        val now = at(25, 16, 20)
        // Earlier today than now + 15 min → the minimum.
        assertEquals(at(25, 16, 35), ComposerTimes.openPostTime(DayChoice.TODAY, LocalTime.of(9, 0), now, zone))
        assertEquals(at(25, 20, 30), ComposerTimes.openPostTime(DayChoice.TODAY, LocalTime.of(20, 30), now, zone))
        // Tomorrow 9pm is past the rolling 24-hour ceiling → the ceiling.
        assertEquals(at(26, 16, 20), ComposerTimes.openPostTime(DayChoice.TOMORROW, LocalTime.of(21, 0), now, zone))
        assertEquals(at(26, 9, 0), ComposerTimes.openPostTime(DayChoice.TOMORROW, LocalTime.of(9, 0), now, zone))
    }

    @Test
    fun postTime_reclampsAStalePick_andFallsBackToTomorrowWhenTodayRanOut() {
        // Picked 4:40pm, sheet sat open until 4:30pm → now + 15 = 4:45pm.
        assertEquals(at(25, 16, 45), ComposerTimes.postTime(DayChoice.TODAY, at(25, 16, 40), at(25, 16, 30), zone))
        // Picked 11:59pm today, submitted at 11:50pm → Today is gone; Tomorrow's range applies.
        assertEquals(at(26, 0), ComposerTimes.postTime(DayChoice.TODAY, at(25, 23, 59), at(25, 23, 50), zone))
        // Tomorrow's ceiling moves with the clock.
        assertEquals(at(26, 16, 30), ComposerTimes.postTime(DayChoice.TOMORROW, at(26, 18, 0), at(25, 16, 30), zone))
    }

    @Test
    fun anyFutureTime_allowsAnyFutureDate_butNeverThePast() {
        val now = at(25, 16, 20, 30)
        assertEquals(at(30, 10, 15), ComposerTimes.anyFutureTime(LocalDate.of(2026, 9, 30), LocalTime.of(10, 15), now, zone))
        assertEquals(at(25, 16, 21), ComposerTimes.anyFutureTime(LocalDate.of(2026, 9, 25), LocalTime.of(9, 0), now, zone))
    }
}
