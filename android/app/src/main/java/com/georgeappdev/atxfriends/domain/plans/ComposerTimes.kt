package com.georgeappdev.atxfriends.domain.plans

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** `.openPost`'s When segment. The other modes never use it. */
enum class DayChoice { TODAY, TOMORROW }

/** A closed range of instants, like Swift's `ClosedRange<Date>`. */
data class TimeRange(val lower: Instant, val upper: Instant) {
    fun clamp(date: Instant): Instant = minOf(maxOf(date, lower), upper)
}

/**
 * ProposePlanSheet's time helpers, as pure functions of the clock and time zone.
 */
object ComposerTimes {
    /** The "Today" minimum lead time, shared with the Today tab's open-slot generator. */
    val MINIMUM_LEAD: Duration = Duration.ofMinutes(15)

    /** The rolling "Tomorrow" ceiling: exactly 24 hours from now. */
    val POST_WINDOW: Duration = Duration.ofHours(24)

    /** `.proposal` / `.groupInvite` default: the next full hour (no day bound). */
    fun nextHourRoundedUp(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()

    fun startOfTomorrow(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

    /** One second before midnight, as iOS computes it. */
    fun endOfToday(now: Instant, zone: ZoneId): Instant = startOfTomorrow(now, zone).minusSeconds(1)

    /** True while at least one 15-minute-out slot is left today. */
    fun todayIsAvailable(now: Instant, zone: ZoneId): Boolean = now.plus(MINIMUM_LEAD) <= endOfToday(now, zone)

    fun defaultDayChoice(now: Instant, zone: ZoneId): DayChoice =
        if (todayIsAvailable(now, zone)) DayChoice.TODAY else DayChoice.TOMORROW

    /** "Today": [now + 15 min, end of today]; collapses to the end of today once that's past. */
    fun todayRange(now: Instant, zone: ZoneId): TimeRange {
        val minimum = now.plus(MINIMUM_LEAD)
        val maximum = endOfToday(now, zone)
        return if (minimum <= maximum) TimeRange(minimum, maximum) else TimeRange(maximum, maximum)
    }

    /** "Tomorrow": [start of tomorrow, now + 24 h] — a rolling ceiling, not the end of tomorrow. */
    fun tomorrowRange(now: Instant, zone: ZoneId): TimeRange {
        val minimum = startOfTomorrow(now, zone)
        val maximum = now.plus(POST_WINDOW)
        return if (minimum <= maximum) TimeRange(minimum, maximum) else TimeRange(maximum, maximum)
    }

    fun range(choice: DayChoice, now: Instant, zone: ZoneId): TimeRange =
        if (choice == DayChoice.TODAY) todayRange(now, zone) else tomorrowRange(now, zone)

    /** `.openPost`'s default (and what switching Today/Tomorrow resets to): next full hour, clamped. */
    fun defaultPickerTime(choice: DayChoice, now: Instant, zone: ZoneId): Instant =
        range(choice, now, zone).clamp(nextHourRoundedUp(now, zone))

    /**
     * What `.openPost` actually posts: the picked time re-clamped against the live range, since
     * the sheet may have sat open (iOS `submit()`). If Today has run out it falls back to the
     * Tomorrow range — without changing the visible segment, exactly as iOS does.
     */
    fun postTime(choice: DayChoice, picked: Instant, now: Instant, zone: ZoneId): Instant {
        val effective = if (todayIsAvailable(now, zone)) choice else DayChoice.TOMORROW
        return range(effective, now, zone).clamp(picked)
    }

    /**
     * `.openPost` time-picker result: the picked clock time on the segment's calendar day,
     * clamped into the segment's range (iOS's compact DatePicker won't go outside `in:`).
     */
    fun openPostTime(choice: DayChoice, time: LocalTime, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val day = if (choice == DayChoice.TODAY) today else today.plusDays(1)
        return range(choice, now, zone).clamp(day.atTime(time).atZone(zone).toInstant())
    }

    /**
     * `.proposal` / `.groupInvite`: the graphical picker's date + time, never earlier than now
     * (iOS `in: Date()...`). A past pick snaps up to the next whole minute.
     */
    fun anyFutureTime(date: LocalDate, time: LocalTime, now: Instant, zone: ZoneId): Instant {
        val picked = date.atTime(time.truncatedTo(ChronoUnit.MINUTES)).atZone(zone).toInstant()
        val earliest = now.atZone(zone).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1).toInstant()
        return if (picked < now) earliest else picked
    }
}
