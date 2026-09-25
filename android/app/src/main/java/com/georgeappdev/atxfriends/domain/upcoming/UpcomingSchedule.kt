package com.georgeappdev.atxfriends.domain.upcoming

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.domain.openslots.ActivitySuggestionTier
import com.georgeappdev.atxfriends.domain.openslots.OpenSlot
import com.georgeappdev.atxfriends.domain.openslots.OpenSlotGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The week strip's dot for one day: filled = a real plan, hollow = only a ghost slot, none = nothing. */
enum class DayDot { PLAN, SLOT, NONE }

/** One day of the 7-day strip: its real plans, or — only when it has none — one ghost slot. */
data class UpcomingDay(val date: LocalDate, val plans: List<GroupPlan>, val ghost: OpenSlot?) {
    val dot: DayDot get() = when {
        plans.isNotEmpty() -> DayDot.PLAN
        ghost != null -> DayDot.SLOT
        else -> DayDot.NONE
    }
}

/** The current user's standing on a plan (iOS `myStatusText`). */
enum class MyPlanStatus { HOSTING, GOING, CANT_MAKE, INVITED }

/** Port of GroupPlansViewModel's derived values and UpcomingView's day layout. */
object UpcomingSchedule {
    const val MAX_GHOST_SLOTS = 7

    /** Active plans I host or am invited to, still in the future, soonest first. */
    fun upcomingPlans(all: List<GroupPlan>, myID: String, now: Instant): List<GroupPlan> =
        all.filter {
            it.status == GroupPlanStatus.ACTIVE && it.date > now && (it.hostID == myID || myID in it.inviteeIDs)
        }.sortedBy { it.date }

    /**
     * Upcoming plans later today: the "Today" section above the strip, which starts tomorrow.
     * (Without it, an invite for later today — the composer's default time — was saved but
     * shown nowhere, on iOS too.)
     */
    fun todayPlans(upcoming: List<GroupPlan>, now: Instant, zone: ZoneId): List<GroupPlan> {
        val today = now.atZone(zone).toLocalDate()
        return upcoming.filter { it.date.atZone(zone).toLocalDate() == today }
    }

    /** Upcoming plans after the strip's last day (today + 7): the "Later" section. */
    fun laterPlans(upcoming: List<GroupPlan>, now: Instant, zone: ZoneId): List<GroupPlan> {
        val lastStripDay = now.atZone(zone).toLocalDate().plusDays(7)
        return upcoming.filter { it.date.atZone(zone).toLocalDate().isAfter(lastStripDay) }
    }

    /** Tomorrow through +7 days — the strip's range. Today and later have their own sections. */
    fun next7Days(now: Instant, zone: ZoneId): List<LocalDate> {
        val today = now.atZone(zone).toLocalDate()
        return (1L..7L).map { today.plusDays(it) }
    }

    /**
     * At most one ghost suggestion per calendar day (the earliest), generated over
     * [start of tomorrow, start of today + 7 days] from the user's own usual slots, preferring
     * planned-tier activities and skipping exact clashes with real plan times. As on iOS, the
     * window ends at midnight *starting* the 7th strip day, so that day never gets a ghost.
     */
    fun openSlotsByDay(
        daySlotCombos: List<DaySlotCombo>,
        activityNames: List<String>,
        upcoming: List<GroupPlan>,
        now: Instant,
        zone: ZoneId,
    ): Map<LocalDate, OpenSlot> {
        val today = now.atZone(zone).toLocalDate()
        val start = today.plusDays(1).atStartOfDay(zone).toInstant()
        val end = today.plusDays(7).atStartOfDay(zone).toInstant()
        val slots = OpenSlotGenerator.generate(
            daySlotCombos = daySlotCombos,
            activities = OpenSlotGenerator.activities(activityNames, ActivitySuggestionTier.PLANNED),
            now = start,
            end = end,
            existingPlanStarts = upcoming.map { it.date },
            maxCount = MAX_GHOST_SLOTS,
            zone = zone,
        )
        val byDay = linkedMapOf<LocalDate, OpenSlot>()
        for (slot in slots) byDay.putIfAbsent(slot.start.atZone(zone).toLocalDate(), slot)
        return byDay
    }

    /** The 7 strip days; a day with any real plan never shows a ghost slot. */
    fun days(upcoming: List<GroupPlan>, slotsByDay: Map<LocalDate, OpenSlot>, now: Instant, zone: ZoneId): List<UpcomingDay> =
        next7Days(now, zone).map { day ->
            val plans = upcoming.filter { it.date.atZone(zone).toLocalDate() == day }
            UpcomingDay(day, plans, if (plans.isEmpty()) slotsByDay[day] else null)
        }

    /** iOS `hasAnyContent`: any upcoming plan at all (even beyond the strip), or any ghost slot. */
    fun hasAnyContent(upcoming: List<GroupPlan>, slotsByDay: Map<LocalDate, OpenSlot>): Boolean =
        upcoming.isNotEmpty() || slotsByDay.isNotEmpty()

    fun goingCount(plan: GroupPlan): Int = plan.responses.values.count { it == GroupPlanResponse.GOING }

    fun myStatus(plan: GroupPlan, myID: String): MyPlanStatus = when {
        plan.hostID == myID -> MyPlanStatus.HOSTING
        plan.responses[myID] == GroupPlanResponse.GOING -> MyPlanStatus.GOING
        plan.responses[myID] == GroupPlanResponse.CANT_MAKE -> MyPlanStatus.CANT_MAKE
        else -> MyPlanStatus.INVITED
    }
}
