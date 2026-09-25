package com.georgeappdev.atxfriends.domain.upcoming

import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UpcomingScheduleTest {

    private val zone = ZoneId.of("America/Chicago")
    /** Friday 2026-09-25, 4pm in Austin. The strip runs Sat 26 … Fri Oct 2. */
    private val now = at(25, 16)
    private fun at(day: Int, hour: Int, month: Int = 9): Instant = LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant()
    private fun date(day: Int, month: Int = 9) = LocalDate.of(2026, month, day)

    private fun plan(
        id: String, date: Instant, host: String = "me", invitees: List<String> = listOf("bea"),
        status: GroupPlanStatus = GroupPlanStatus.ACTIVE, responses: Map<String, GroupPlanResponse> = invitees.associateWith { GroupPlanResponse.INVITED },
    ) = GroupPlan(
        id, host, invitees, responses, Activity("a", "Chess", false, Instant.EPOCH, true),
        null, null, null, null, date, status, Instant.EPOCH, Instant.EPOCH,
    )

    // Every evening + Saturday wake-up: more than one slot on some days.
    private val combos = listOf("Saturday_Wake Up", "Saturday_Evening", "Sunday_Evening", "Monday_Evening", "Tuesday_Evening",
        "Wednesday_Evening", "Thursday_Evening", "Friday_Evening").map(::DaySlotCombo)

    @Test
    fun upcomingPlans_activeFutureMineOnly_soonestFirst() {
        val all = listOf(
            plan("later", at(28, 18)),
            plan("soon", at(26, 18), host = "sam", invitees = listOf("me")),
            plan("past", at(25, 10)),
            plan("cancelled", at(27, 18), status = GroupPlanStatus.CANCELLED),
            plan("notMine", at(27, 18), host = "sam", invitees = listOf("bea")),
        )
        assertEquals(listOf("soon", "later"), UpcomingSchedule.upcomingPlans(all, "me", now).map { it.id })
    }

    @Test
    fun strip_isTomorrowThroughPlusSeven() {
        assertEquals((26..30).map { date(it) } + listOf(date(1, 10), date(2, 10)), UpcomingSchedule.next7Days(now, zone))
    }

    @Test
    fun ghosts_atMostOnePerDay_theEarliest_andNeverOnTheLastStripDay() {
        val slots = UpcomingSchedule.openSlotsByDay(combos, listOf("Hiking", "Tacos"), emptyList(), now, zone)
        // Saturday has Wake Up and Evening; only the 7am one is kept.
        assertEquals(at(26, 7), slots[date(26)]!!.start)
        assertEquals(TimeSlot.WAKE_UP, slots[date(26)]!!.timeSlot)
        // The window ends at midnight starting Fri Oct 2, as on iOS.
        assertNull(slots[date(2, 10)])
        assertEquals((26..30).map { date(it) } + date(1, 10), slots.keys.toList())
    }

    @Test
    fun ghosts_preferPlannedTierActivities() {
        // Hiking (outdoor) is "planned", Tacos (food) is "spontaneous".
        val slots = UpcomingSchedule.openSlotsByDay(combos, listOf("Tacos", "Hiking"), emptyList(), now, zone)
        // Activities rotate through the generated slots (Sat 7am, Sat 5pm, Sun 5pm, Mon 5pm…).
        assertEquals("Hiking", slots[date(26)]!!.activityName)
        assertEquals("Hiking", slots[date(27)]!!.activityName)
        assertEquals("Tacos", slots[date(28)]!!.activityName)
    }

    @Test
    fun ghosts_skipASlotAtTheSameHourAsARealPlan_butKeepOthersThatDay() {
        val upcoming = listOf(plan("sat", at(26, 7)))
        val slots = UpcomingSchedule.openSlotsByDay(combos, listOf("Hiking"), upcoming, now, zone)
        assertEquals(at(26, 17), slots[date(26)]!!.start)
    }

    @Test
    fun days_suppressTheGhostOnAnyDayWithARealPlan_andDotsFollow() {
        val upcoming = listOf(plan("sun", at(27, 12)))
        val slots = UpcomingSchedule.openSlotsByDay(combos, listOf("Hiking"), upcoming, now, zone)
        val days = UpcomingSchedule.days(upcoming, slots, now, zone)

        val sunday = days.first { it.date == date(27) }
        assertEquals(listOf("sun"), sunday.plans.map { it.id })
        assertNull(sunday.ghost)
        assertEquals(DayDot.PLAN, sunday.dot)

        assertEquals(DayDot.SLOT, days.first { it.date == date(26) }.dot)
        assertEquals(DayDot.NONE, days.first { it.date == date(2, 10) }.dot)
    }

    @Test
    fun noCombosOrActivities_noGhosts() {
        assertTrue(UpcomingSchedule.openSlotsByDay(emptyList(), listOf("Hiking"), emptyList(), now, zone).isEmpty())
        assertTrue(UpcomingSchedule.openSlotsByDay(combos, emptyList(), emptyList(), now, zone).isEmpty())
    }

    @Test
    fun hasAnyContent_countsPlansBeyondTheStripToo() {
        assertFalse(UpcomingSchedule.hasAnyContent(emptyList(), emptyMap()))
        assertTrue(UpcomingSchedule.hasAnyContent(listOf(plan("far", at(20, 12, month = 10))), emptyMap()))
    }

    @Test
    fun goingCountAndMyStatus() {
        val p = plan(
            "p", at(27, 18), host = "host", invitees = listOf("me", "bea", "cy"),
            responses = mapOf("me" to GroupPlanResponse.CANT_MAKE, "bea" to GroupPlanResponse.GOING, "cy" to GroupPlanResponse.GOING),
        )
        assertEquals(2, UpcomingSchedule.goingCount(p))
        assertEquals(MyPlanStatus.CANT_MAKE, UpcomingSchedule.myStatus(p, "me"))
        assertEquals(MyPlanStatus.GOING, UpcomingSchedule.myStatus(p, "bea"))
        assertEquals(MyPlanStatus.HOSTING, UpcomingSchedule.myStatus(p, "host"))
        assertEquals(MyPlanStatus.INVITED, UpcomingSchedule.myStatus(p.copy(responses = emptyMap()), "me"))
    }

    // region Today / Later — every upcoming plan shows somewhere (the "invite vanished" bug)

    @Test
    fun inviteForLaterToday_showsInToday_notTheStrip() {
        val sixPm = plan("today", at(25, 18)) // now is 4pm; the composer's default is the next hour today
        val upcoming = UpcomingSchedule.upcomingPlans(listOf(sixPm), "me", now)
        assertEquals(listOf(sixPm), UpcomingSchedule.todayPlans(upcoming, now, zone))
        assertTrue(UpcomingSchedule.laterPlans(upcoming, now, zone).isEmpty())
        assertTrue(UpcomingSchedule.days(upcoming, emptyMap(), now, zone).all { it.plans.isEmpty() })
    }

    @Test
    fun inviteTenDaysOut_showsInLater() {
        val tenDays = plan("later", now.plus(java.time.Duration.ofDays(10)))
        val upcoming = UpcomingSchedule.upcomingPlans(listOf(tenDays), "me", now)
        assertEquals(listOf(tenDays), UpcomingSchedule.laterPlans(upcoming, now, zone))
        assertTrue(UpcomingSchedule.todayPlans(upcoming, now, zone).isEmpty())
    }

    @Test
    fun everyUpcomingPlan_landsInExactlyOnePlace() {
        // Hourly for 12 days, starting just after now.
        val all = (1..12 * 24).map { h -> plan("p$h", now.plus(java.time.Duration.ofHours(h.toLong()))) }
        val upcoming = UpcomingSchedule.upcomingPlans(all, "me", now)
        val placed = UpcomingSchedule.todayPlans(upcoming, now, zone) +
            UpcomingSchedule.days(upcoming, emptyMap(), now, zone).flatMap { it.plans } +
            UpcomingSchedule.laterPlans(upcoming, now, zone)
        assertEquals(upcoming.map { it.id }.sorted(), placed.map { it.id }.sorted())
    }

    // endregion
}
