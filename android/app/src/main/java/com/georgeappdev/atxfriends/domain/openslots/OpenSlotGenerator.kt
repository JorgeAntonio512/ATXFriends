package com.georgeappdev.atxfriends.domain.openslots

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.domain.matching.ActivityCategories
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * iOS `OpenSlot`: a suggested-but-unposted plan built only from the user's own profile.
 * Never written to Firestore. [activityName] is the raw profile name (no "?").
 */
data class OpenSlot(
    val id: String,
    val start: Instant,
    val activityName: String,
    val dayOfWeek: DayOfWeek,
    val timeSlot: TimeSlot,
)

/** Where a Today ghost card came from: the user's own recurring slots, or the fixed fallback times. */
enum class OpenSlotSource { USUAL, FALLBACK }

data class RankedOpenSlot(val slot: OpenSlot, val source: OpenSlotSource) {
    val id: String get() = slot.id
}

/** iOS `ActivitySuggestionTier`: Today prefers spontaneous activities, Upcoming planned ones. */
enum class ActivitySuggestionTier { SPONTANEOUS, PLANNED }

/** iOS `ActivityCategory.suggestionTier`. */
val ActivityCategory.suggestionTier: ActivitySuggestionTier
    get() = when (this) {
        ActivityCategory.FOOD_AND_DRINK, ActivityCategory.ENTERTAINMENT_AND_SOCIAL,
        ActivityCategory.WELLNESS_AND_SELF_CARE, ActivityCategory.LEARNING_AND_EDUCATION,
        ActivityCategory.HOBBIES_AND_CRAFTS, ActivityCategory.UNIQUE_AND_NICHE,
        -> ActivitySuggestionTier.SPONTANEOUS
        ActivityCategory.SPORTS_AND_FITNESS, ActivityCategory.OUTDOOR_AND_NATURE,
        ActivityCategory.ARTS_AND_CULTURE, ActivityCategory.MUSIC_AND_PERFORMANCE,
        ActivityCategory.TRAVEL_AND_ADVENTURE, ActivityCategory.COMMUNITY_AND_VOLUNTEERING,
        ActivityCategory.PROFESSIONAL_AND_BUSINESS,
        -> ActivitySuggestionTier.PLANNED
    }

/** iOS `TimeSlot.startHour`. */
val TimeSlot.startHour: Int
    get() = when (this) {
        TimeSlot.WAKE_UP -> 7
        TimeSlot.AFTERNOON -> 12
        TimeSlot.EVENING -> 17
        TimeSlot.NIGHT -> 21
        TimeSlot.OWL_HOURS -> 2
        TimeSlot.UNKNOWN -> error("UNKNOWN time slot has no start hour")
    }

/**
 * Port of iOS `OpenSlotGenerator`: pure and deterministic — same inputs, same slots, same order.
 * [ZoneId] plays the role of iOS's `calendar:` parameter (the device time zone in the app).
 */
object OpenSlotGenerator {
    /** Mirrors ProposePlanSheet's "Today" minimum lead time: nothing under 15 minutes out. */
    val MINIMUM_LEAD_TIME: Duration = Duration.ofMinutes(15)

    /** Today's search window: exactly 24 hours from now. */
    val TODAY_WINDOW: Duration = Duration.ofHours(24)

    data class FallbackTime(val hour: Int, val minute: Int, val bucket: TimeSlot)

    /** Fixed clock times that top up Today's cards when the user's own slots don't fill them. */
    val FALLBACK_CANDIDATE_TIMES = listOf(
        FallbackTime(8, 0, TimeSlot.WAKE_UP),
        FallbackTime(12, 0, TimeSlot.AFTERNOON),
        FallbackTime(19, 0, TimeSlot.EVENING),
    )

    const val TODAY_MAX_COUNT = 3

    /**
     * Activities in [tier] first, then the rest, each group in profile order. Activities with no
     * category (custom user-typed ones) count as spontaneous.
     */
    fun activities(names: List<String>, preferring: ActivitySuggestionTier): List<String> {
        fun tierOf(name: String) = ActivityCategories.category(name)?.suggestionTier ?: ActivitySuggestionTier.SPONTANEOUS
        val (preferred, rest) = names.partition { tierOf(it) == preferring }
        return preferred + rest
    }

    /** The user's own recurring slots in [now, end], skipping ones too soon or clashing with a plan. */
    fun generate(
        daySlotCombos: List<DaySlotCombo>,
        activities: List<String>,
        now: Instant,
        end: Instant,
        existingPlanStarts: List<Instant>,
        maxCount: Int,
        zone: ZoneId,
    ): List<OpenSlot> {
        // iOS drops combos it can't parse when it reads the profile.
        val combos = daySlotCombos.filter { it.isKnown }
        if (combos.isEmpty() || activities.isEmpty()) return emptyList()

        val earliestAllowed = now.plus(MINIMUM_LEAD_TIME)
        val candidates = mutableListOf<Pair<DaySlotCombo, Instant>>()
        for (combo in orderedByWeekday(combos)) {
            for (date in occurrences(combo, now, end, zone)) {
                if (date < earliestAllowed) continue
                if (overlapsExistingPlan(date, existingPlanStarts, zone)) continue
                candidates += combo to date
            }
        }
        candidates.sortBy { it.second }

        return candidates.take(maxCount).mapIndexed { index, (combo, date) ->
            OpenSlot(
                id = "${combo.day.raw}_${combo.slot.raw}_${date.epochSecond}",
                start = date,
                activityName = activities[index % activities.size],
                dayOfWeek = combo.day,
                timeSlot = combo.slot,
            )
        }
    }

    /**
     * Today's cards: the user's usual slots in the next 24 hours, topped up to [maxCount] with
     * the fallback clock times. Usual slots always come before fallback ones.
     */
    fun generateForToday(
        daySlotCombos: List<DaySlotCombo>,
        activities: List<String>,
        now: Instant,
        existingPlanStarts: List<Instant>,
        maxCount: Int = TODAY_MAX_COUNT,
        zone: ZoneId,
    ): List<RankedOpenSlot> {
        val end = now.plus(TODAY_WINDOW)
        val usual = generate(daySlotCombos, activities, now, end, existingPlanStarts, maxCount, zone)
        val ranked = usual.map { RankedOpenSlot(it, OpenSlotSource.USUAL) }.toMutableList()

        if (ranked.size < maxCount && activities.isNotEmpty()) {
            val earliestAllowed = now.plus(MINIMUM_LEAD_TIME)
            val usedStarts = ranked.map { it.slot.start }.toMutableSet()
            val today = now.atZone(zone).toLocalDate()

            val fallbackCandidates = mutableListOf<Pair<Instant, TimeSlot>>()
            for (time in FALLBACK_CANDIDATE_TIMES) {
                for (dayOffset in 0L..1L) {
                    val candidate = at(today.plusDays(dayOffset), time.hour, time.minute, zone)
                    // Any 24-hour window holds exactly one occurrence of a fixed clock time.
                    if (candidate >= now && candidate <= end) {
                        fallbackCandidates += candidate to time.bucket
                        break
                    }
                }
            }
            fallbackCandidates.sortBy { it.first }

            // As on iOS, the activity rotation index counts skipped candidates too.
            for ((index, entry) in fallbackCandidates.withIndex()) {
                if (ranked.size >= maxCount) break
                val (date, bucket) = entry
                if (date < earliestAllowed) continue
                if (date in usedStarts || overlapsExistingPlan(date, existingPlanStarts, zone)) continue
                val slot = OpenSlot(
                    id = "fallback_${date.epochSecond}",
                    start = date,
                    activityName = activities[(usual.size + index) % activities.size],
                    dayOfWeek = dayOfWeek(date.atZone(zone)),
                    timeSlot = bucket,
                )
                ranked += RankedOpenSlot(slot, OpenSlotSource.FALLBACK)
                usedStarts += date
            }
        }
        return ranked
    }

    /**
     * The earliest occurrence of any usual slot in [now, end], ignoring lead time and existing
     * plans. Backs Today's "Your next usual slot" link; never postable.
     */
    fun nextUsualOccurrence(daySlotCombos: List<DaySlotCombo>, now: Instant, end: Instant, zone: ZoneId): OpenSlot? {
        var earliest: Pair<DaySlotCombo, Instant>? = null
        for (combo in orderedByWeekday(daySlotCombos.filter { it.isKnown })) {
            for (date in occurrences(combo, now, end, zone)) {
                if (earliest == null || date < earliest.second) earliest = combo to date
            }
        }
        val (combo, date) = earliest ?: return null
        return OpenSlot("next_${combo.day.raw}_${combo.slot.raw}", date, "", combo.day, combo.slot)
    }

    // Shared helpers

    /** Monday-first, then by slot start hour — independent of stored order. */
    private fun orderedByWeekday(combos: List<DaySlotCombo>): List<DaySlotCombo> =
        combos.sortedWith(compareBy({ it.day.ordinal }, { it.slot.startHour }))

    /** Same calendar day and same clock hour as any existing plan. */
    private fun overlapsExistingPlan(date: Instant, existingPlanStarts: List<Instant>, zone: ZoneId): Boolean {
        val hour = date.atZone(zone).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()
        return existingPlanStarts.any { it.atZone(zone).truncatedTo(ChronoUnit.HOURS).toLocalDateTime() == hour }
    }

    /** Every start of [combo] within [from, through], walking day by day from the start of [from]'s day. */
    private fun occurrences(combo: DaySlotCombo, from: Instant, through: Instant, zone: ZoneId): List<Instant> {
        val results = mutableListOf<Instant>()
        var day = from.atZone(zone).toLocalDate()
        val weekday = javaDayOf(combo.day)
        var guard = 0
        // Bounded walk, like iOS: covers Upcoming's 7-day window with room to spare.
        while (day.atStartOfDay(zone).toInstant() <= through && guard < 400) {
            guard++
            if (day.dayOfWeek == weekday) {
                val candidate = at(day, combo.slot.startHour, 0, zone)
                if (candidate >= from && candidate <= through) results += candidate
            }
            day = day.plusDays(1)
        }
        return results
    }

    private fun at(day: LocalDate, hour: Int, minute: Int, zone: ZoneId): Instant =
        ZonedDateTime.of(day, LocalTime.of(hour, minute), zone).toInstant()

    private fun javaDayOf(day: DayOfWeek): java.time.DayOfWeek = when (day) {
        DayOfWeek.MONDAY -> java.time.DayOfWeek.MONDAY
        DayOfWeek.TUESDAY -> java.time.DayOfWeek.TUESDAY
        DayOfWeek.WEDNESDAY -> java.time.DayOfWeek.WEDNESDAY
        DayOfWeek.THURSDAY -> java.time.DayOfWeek.THURSDAY
        DayOfWeek.FRIDAY -> java.time.DayOfWeek.FRIDAY
        DayOfWeek.SATURDAY -> java.time.DayOfWeek.SATURDAY
        DayOfWeek.SUNDAY -> java.time.DayOfWeek.SUNDAY
        DayOfWeek.UNKNOWN -> error("UNKNOWN day has no weekday")
    }

    private fun dayOfWeek(date: ZonedDateTime): DayOfWeek = DayOfWeek.entries.first { it != DayOfWeek.UNKNOWN && javaDayOf(it) == date.dayOfWeek }
}
