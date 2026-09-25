package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Every date- and time-derived string the Messages screens show, as a given moment [now] in
 * [zone]/[locale]. Each function is a port of a specific iOS formatter, noted per function;
 * the wording (including "Tmrw" and lowercase am/pm) is iOS's own.
 */
class MessageDates(
    val now: Instant,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val today: LocalDate = now.atZone(zone).toLocalDate()

    private fun local(instant: Instant): ZonedDateTime = instant.atZone(zone)
    private fun day(instant: Instant): LocalDate = local(instant).toLocalDate()
    private fun format(instant: Instant, pattern: String) =
        DateTimeFormatter.ofPattern(pattern, locale).format(local(instant))

    private fun isToday(instant: Instant) = day(instant) == today
    private fun isTomorrow(instant: Instant) = day(instant) == today.plusDays(1)

    /** iOS `.formatted(date: .omitted, time: .shortened)`, e.g. "2:30 PM". */
    fun shortTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local(instant))

    /** iOS "h:mma" with lowercase am/pm symbols and ":00" removed: "7pm", "7:30pm". */
    private fun compactTime(instant: Instant): String {
        val t = local(instant)
        val suffix = if (t.hour < 12) "am" else "pm"
        return (format(instant, "h:mm") + suffix).replace(":00", "")
    }

    /** Row plan pill, e.g. "Poker · Sat 7pm" (MessageThread.planPillText). Null without a confirmed date. */
    fun planPill(plan: Plan): String? {
        val date = plan.confirmedDate ?: return null
        val dayText = when {
            isToday(date) -> "Today"
            isTomorrow(date) -> "Tmrw"
            else -> format(date, "EEE")
        }
        return "${plan.activity.name} · $dayText ${compactTime(date)}"
    }

    /** Row timestamp for the last message (MessageThread.timeText). */
    fun rowTime(sentAt: Instant): String = when {
        isToday(sentAt) -> format(sentAt, "h:mm a")
        day(sentAt) == today.minusDays(1) -> "Yesterday"
        ChronoUnit.DAYS.between(local(sentAt), local(now)) < 7 -> format(sentAt, "EEEE")
        else -> format(sentAt, "M/d/yy")
    }

    /**
     * Shared by the pinned card headline and the confirmed-plan empty state (planDayWord):
     * "Tonight" (today, 5pm or later), "Today", "Tomorrow", or e.g. "Sat, Oct 3".
     */
    fun planDayWord(date: Instant): String = when {
        isToday(date) -> if (local(date).hour >= 17) "Tonight" else "Today"
        isTomorrow(date) -> "Tomorrow"
        else -> format(date, "EEE, MMM d")
    }

    /** Pinned card headline, e.g. "Tonight · 7:00 PM". */
    fun pinnedHeadline(plan: Plan): String {
        val date = plan.standingDate ?: return plan.activity.name
        return "${planDayWord(date)} · ${format(date, "h:mm a")}"
    }

    /** Pinned card subline: the activity, plus the countdown when there is one. */
    fun pinnedSubline(plan: Plan): String {
        val date = plan.standingDate ?: return plan.activity.name
        return countdown(date)?.let { "${plan.activity.name} · $it" } ?: plan.activity.name
    }

    /**
     * "starts in N min" (never less than 1) up to an hour out, "starts in N hr" up to 3 hours
     * out, "happening now" for 2 hours after the start, otherwise null (countdownSuffix).
     */
    fun countdown(date: Instant): String? {
        val diff = Duration.between(now, date).toMillis() / 1000.0
        return when {
            diff >= 0 && diff <= 3 * 3600 -> {
                val minutes = Math.round(diff / 60)
                if (minutes < 60) "starts in ${maxOf(minutes, 1L)} min"
                else "starts in ${Math.round(diff / 3600)} hr"
            }
            diff < 0 && diff >= -2 * 3600 -> "happening now"
            else -> null
        }
    }

    /** "You're on for tonight." / "You're on for Sat, Oct 3." (emptyStateConfirmedPlanTitle). */
    fun confirmedEmptyTitle(date: Instant): String {
        val word = planDayWord(date)
        return when (word) {
            "Tonight", "Today", "Tomorrow" -> "You're on for ${word.lowercase()}."
            else -> "You're on for $word."
        }
    }

    /** Plan proposal card date: "Today at 7pm", "Tomorrow at 7:30pm", "Sat, Oct 3 at 7pm". */
    fun proposalDate(date: Instant): String = when {
        isToday(date) -> "Today at ${compactTime(date)}"
        isTomorrow(date) -> "Tomorrow at ${compactTime(date)}"
        else -> "${format(date, "EEE, MMM d")} at ${compactTime(date)}"
    }

    /** RescheduleRequestView.formatted: "Today, 3:00 PM", "Sunday, 3:00 PM", "Sun, Oct 5, 3:00 PM". */
    fun rescheduleTime(date: Instant): String {
        val time = shortTime(date)
        if (isToday(date)) return "Today, $time"
        if (isTomorrow(date)) return "Tomorrow, $time"
        val daysAway = ChronoUnit.DAYS.between(today, day(date))
        val dayText = if (daysAway in 0..6) format(date, "EEEE") else format(date, "EEE, MMM d")
        return "$dayText, $time"
    }

    /** The reschedule line on the pinned card, e.g. "Sam suggested Sunday, 3:00 PM." */
    fun rescheduleSuggestion(plan: Plan, myID: String, otherUserName: String): String {
        val whenText = plan.pendingRescheduleDate?.let(::rescheduleTime) ?: "a new time"
        return when (plan.counterProposedBy) {
            null -> "A new time was suggested: $whenText."
            myID -> "You suggested $whenText."
            else -> "$otherUserName suggested $whenText."
        }
    }

    /**
     * The thread's date header for messages sent on [day]. iOS always shows the literal "Today"
     * (a known bug, spec §11.2); Android shows the real day: "Today", "Yesterday", a weekday
     * within the last week, "Mon, Sep 1" earlier this year, "Sep 1, 2025" before that.
     */
    fun dateHeader(day: LocalDate): String {
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo == 0L -> "Today"
            daysAgo == 1L -> "Yesterday"
            daysAgo in 2..6 -> DateTimeFormatter.ofPattern("EEEE", locale).format(day)
            day.year == today.year -> DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(day)
            else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(day)
        }
    }

    /** The local calendar day a message was sent on. */
    fun dayOf(instant: Instant): LocalDate = day(instant)
}

/** Collapsed pinned-card subtitle, e.g. "Poker · Mozart's" (collapsedSubtitle). */
fun Plan.collapsedSubtitle(): String {
    if (status == PlanStatus.COUNTER_PROPOSED) {
        return "${activity.name} · New time suggested"
    }
    locationName?.takeIf { it.isNotEmpty() }?.let { return "${activity.name} · $it" }
    location?.takeIf { it.isNotEmpty() }?.let { return "${activity.name} · $it" }
    return activity.name
}
