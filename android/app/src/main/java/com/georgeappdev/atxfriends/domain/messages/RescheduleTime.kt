package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Plan
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The time rules of iOS ReschedulePlanSheet. */
object RescheduleTime {

    /**
     * Where the picker starts: the plan's confirmed time if it's still ahead, otherwise an hour
     * from now (`base > Date() ? base : Date() + 3600`).
     */
    fun initial(plan: Plan, now: Instant): Instant {
        val base = plan.confirmedDate ?: now
        return if (base > now) base else now.plus(Duration.ofHours(1))
    }

    /** iOS's picker only allows `Date()...`: the suggested time must still be ahead when sent. */
    fun isAllowed(time: Instant, now: Instant): Boolean = time > now

    /**
     * The moment a date picker day plus a time picker hour/minute mean in [zone]. Material's
     * date picker reports the chosen day as UTC midnight in epoch millis.
     */
    fun combine(dayUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Instant {
        val day = Instant.ofEpochMilli(dayUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
    }

    /** [time]'s day as the date picker's UTC-midnight millis. */
    fun dayUtcMillis(time: Instant, zone: ZoneId): Long =
        time.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Whether a date picker day (UTC-midnight millis) can be picked: today or later in [zone]. */
    fun isSelectableDay(dayUtcMillis: Long, now: Instant, zone: ZoneId): Boolean {
        val day = Instant.ofEpochMilli(dayUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return !day.isBefore(now.atZone(zone).toLocalDate())
    }
}
