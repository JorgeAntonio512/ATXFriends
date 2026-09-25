package com.georgeappdev.atxfriends.domain.today

import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.domain.openslots.OpenSlotSource
import com.georgeappdev.atxfriends.domain.openslots.RankedOpenSlot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** iOS `TodayPlan.isExpired`: the start time has passed. Derived on every read, never stored. */
fun TodayPlan.isExpired(now: Instant): Boolean = scheduledTime < now

/** The ghost-card section header (iOS TodayView.openSlotsSectionHeader). */
enum class OpenSlotsHeader { OR_POST_YOUR_OWN, FREE_IN_THE_NEXT_DAY, YOUR_OPEN_SLOTS }

/** The day word on a plan's time badge. */
enum class BadgeDay { TODAY, TOMORROW, OTHER }

/** Port of the derived state in iOS TodayViewModel and the text helpers in TodayView. */
object TodayFeed {

    /** Distinct activity names among non-expired plans, sorted, for the filter chips. */
    fun availableActivities(openPlans: List<TodayPlan>, now: Instant): List<String> =
        openPlans.filterNot { it.isExpired(now) }.map { it.activity.name }.distinct().sorted()

    /** Non-expired plans, then the activity chip filter (client-side only). */
    fun filteredPlans(openPlans: List<TodayPlan>, activityFilter: String?, now: Instant): List<TodayPlan> {
        val live = openPlans.filterNot { it.isExpired(now) }
        return if (activityFilter == null) live else live.filter { it.activity.name == activityFilter }
    }

    /**
     * iOS listener logic: the query only returns open plans, so one of my own non-expired plans
     * that vanishes from a snapshot must have been claimed. Returns the first such plan.
     */
    fun justClaimed(old: List<TodayPlan>, new: List<TodayPlan>, myID: String, now: Instant): TodayPlan? {
        val newIDs = new.map { it.id }.toSet()
        return old.firstOrNull { it.creatorID == myID && !it.isExpired(now) && it.id !in newIDs }
    }

    fun openSlotsHeader(filteredPlans: List<TodayPlan>, slots: List<RankedOpenSlot>): OpenSlotsHeader = when {
        filteredPlans.isNotEmpty() -> OpenSlotsHeader.OR_POST_YOUR_OWN
        slots.isNotEmpty() && slots.all { it.source == OpenSlotSource.FALLBACK } -> OpenSlotsHeader.FREE_IN_THE_NEXT_DAY
        else -> OpenSlotsHeader.YOUR_OPEN_SLOTS
    }

    /** Whether the badge reads "Today …", "Tomorrow …", or just the time (iOS isDateInToday/Tomorrow). */
    fun badgeDay(date: Instant, now: Instant, zone: ZoneId): BadgeDay {
        val day = date.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (day) {
            today -> BadgeDay.TODAY
            today.plusDays(1) -> BadgeDay.TOMORROW
            else -> BadgeDay.OTHER
        }
    }

    /** "Today 7:00 PM" / "Tomorrow 9:00 AM" / "7:00 PM". [time] is the device's short time format. */
    fun timeBadgeLabel(date: Instant, now: Instant, zone: ZoneId, time: (ZonedDateTime) -> String): String {
        val t = time(date.atZone(zone))
        return when (badgeDay(date, now, zone)) {
            BadgeDay.TODAY -> "Today $t"
            BadgeDay.TOMORROW -> "Tomorrow $t"
            BadgeDay.OTHER -> t
        }
    }

    /** "In 3 hrs · 7pm" / "In 45 min · 5:30pm" / "Now · 7pm" (iOS TodayView.timeUntilLine). */
    fun timeUntilLine(date: Instant, now: Instant, zone: ZoneId): String {
        val local = date.atZone(zone)
        val compact = compactTime(local.hour, local.minute)
        val secondsUntil = Duration.between(now, date).toMillis() / 1000.0
        if (secondsUntil <= 0) return "Now · $compact"
        // Math.round is half-up, which equals Swift's .rounded() for positive values.
        val minutesUntil = Math.round(secondsUntil / 60)
        if (minutesUntil < 60) return "In $minutesUntil min · $compact"
        val hoursUntil = Math.round(secondsUntil / 3600)
        return "In $hoursUntil hr${if (hoursUntil == 1L) "" else "s"} · $compact"
    }

    /** "tonight at 7 PM" / "today at 8 AM" / "tomorrow at 8 AM" — for TalkBack. */
    fun accessibleTimeDescription(date: Instant, now: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val local = date.atZone(zone)
        val isToday = badgeDay(date, now, zone) == BadgeDay.TODAY
        val isEveningHour = local.hour >= 17 || local.hour < 5
        val dayWord = if (isToday) (if (isEveningHour) "tonight" else "today") else "tomorrow"
        val pattern = if (local.minute == 0) "h a" else "h:mm a"
        return "$dayWord at ${java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(local)}"
    }

    /** "Your next usual slot: Monday night" — the slot name lowercased, as on iOS. */
    fun nextUsualSlotLine(day: String, slot: String): String = "Your next usual slot: $day ${slot.lowercase()}"

    /** "7pm", "5:30pm", "12am". */
    fun compactTime(hour: Int, minute: Int): String {
        val displayHour = (hour % 12).let { if (it == 0) 12 else it }
        val suffix = if (hour >= 12) "pm" else "am"
        return if (minute == 0) "$displayHour$suffix" else String.format(Locale.US, "%d:%02d%s", displayHour, minute, suffix)
    }
}
