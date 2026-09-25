package com.georgeappdev.atxfriends.domain.openslots

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * iOS has no OpenSlotGenerator unit tests, so these are equivalents written from
 * OpenSlotGenerator.swift. 2026-09-25 is a Friday; everything runs in Austin's time zone.
 */
class OpenSlotGeneratorTest {

    private val zone = ZoneId.of("America/Chicago")

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(2026, month, day, hour, minute, second).atZone(zone).toInstant()

    private fun friday(hour: Int, minute: Int = 0, second: Int = 0) = at(9, 25, hour, minute, second)
    private fun saturday(hour: Int, minute: Int = 0) = at(9, 26, hour, minute)

    private fun combos(vararg raw: String) = raw.map(::DaySlotCombo)

    private fun today(
        now: Instant,
        combos: List<DaySlotCombo>,
        activities: List<String> = listOf("Tacos"),
        existing: List<Instant> = emptyList(),
    ) = OpenSlotGenerator.generateForToday(combos, activities, now, existing, zone = zone)

    private fun usualStarts(result: List<RankedOpenSlot>) =
        result.filter { it.source == OpenSlotSource.USUAL }.map { it.slot.start }

    // 15-minute lead time

    @Test
    fun slotExactly15MinutesOutIsKept() {
        val result = today(friday(16, 45), combos("Friday_Evening"))
        assertEquals(listOf(friday(17)), usualStarts(result))
    }

    @Test
    fun slotUnder15MinutesOutIsDropped() {
        val result = today(friday(16, 45, 1), combos("Friday_Evening"))
        assertTrue(usualStarts(result).isEmpty())
    }

    @Test
    fun slotThatAlreadyStartedIsDropped() {
        val result = today(friday(17, 5), combos("Friday_Evening"))
        assertTrue(usualStarts(result).isEmpty())
    }

    // 24-hour window edges

    @Test
    fun slotExactly24HoursOutIsKept() {
        // Friday 17:00 is "now" (too soon); Saturday 17:00 is exactly now + 24h.
        val result = today(friday(17), combos("Friday_Evening", "Saturday_Evening"))
        assertEquals(listOf(saturday(17)), usualStarts(result))
    }

    @Test
    fun slotJustPast24HoursIsDropped() {
        // The window ends 1 ms before Saturday 17:00.
        val result = today(friday(17).minusMillis(1), combos("Saturday_Evening"))
        assertTrue(usualStarts(result).isEmpty())
    }

    @Test
    fun sameWeekdayNextWeekIsOutsideTheWindow() {
        val result = today(friday(22), combos("Friday_Night"))
        assertTrue(usualStarts(result).isEmpty())
    }

    // Fallback times

    @Test
    fun fallbackTimesFillCardsWhenNoUsualSlotLands() {
        val result = today(friday(10), combos("Wednesday_Night"), activities = listOf("A", "B"))
        assertEquals(listOf(friday(12), friday(19), saturday(8)), result.map { it.slot.start })
        assertTrue(result.all { it.source == OpenSlotSource.FALLBACK })
        assertEquals(listOf(TimeSlot.AFTERNOON, TimeSlot.EVENING, TimeSlot.WAKE_UP), result.map { it.slot.timeSlot })
        assertEquals(listOf(DayOfWeek.FRIDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), result.map { it.slot.dayOfWeek })
        assertEquals(listOf("A", "B", "A"), result.map { it.slot.activityName })
        assertEquals("fallback_${friday(12).epochSecond}", result[0].slot.id)
    }

    @Test
    fun fallbackUnder15MinutesOutIsSkippedButStillAdvancesTheActivityRotation() {
        val result = today(friday(11, 50), emptyList(), activities = listOf("A", "B", "C"))
        assertEquals(listOf(friday(19), saturday(8)), result.map { it.slot.start })
        // iOS indexes the rotation by candidate position, counting the skipped 12:00.
        assertEquals(listOf("B", "C"), result.map { it.slot.activityName })
    }

    @Test
    fun usualSlotsComeFirstThenFallbacks() {
        val result = today(friday(10), combos("Friday_Night"), activities = listOf("A", "B"))
        assertEquals(listOf(friday(21), friday(12), friday(19)), result.map { it.slot.start })
        assertEquals(listOf(OpenSlotSource.USUAL, OpenSlotSource.FALLBACK, OpenSlotSource.FALLBACK), result.map { it.source })
        assertEquals(listOf("A", "B", "A"), result.map { it.slot.activityName })
    }

    @Test
    fun fallbackAtTheSameTimeAsAUsualSlotIsNotRepeated() {
        val result = today(friday(10), combos("Friday_Afternoon"))
        assertEquals(listOf(friday(12), friday(19), saturday(8)), result.map { it.slot.start })
        assertEquals(OpenSlotSource.USUAL, result[0].source)
    }

    @Test
    fun noActivitiesMeansNoCards() {
        assertTrue(today(friday(10), combos("Friday_Night"), activities = emptyList()).isEmpty())
    }

    @Test
    fun noCombosStillGetsFallbacks() {
        assertEquals(3, today(friday(10), emptyList()).size)
    }

    // Near midnight

    @Test
    fun nearMidnightRollsIntoTomorrow() {
        val result = today(
            friday(23, 50),
            combos("Friday_Night", "Friday_Owl Hours", "Saturday_Owl Hours", "Saturday_Wake Up"),
            activities = listOf("A", "B", "C"),
        )
        assertEquals(listOf(saturday(2), saturday(7), saturday(8)), result.map { it.slot.start })
        assertEquals(listOf(OpenSlotSource.USUAL, OpenSlotSource.USUAL, OpenSlotSource.FALLBACK), result.map { it.source })
        assertEquals(listOf(DayOfWeek.SATURDAY, DayOfWeek.SATURDAY, DayOfWeek.SATURDAY), result.map { it.slot.dayOfWeek })
        assertEquals("Saturday_Owl Hours_${saturday(2).epochSecond}", result[0].slot.id)
        // Fallback activity index = usual count (2) + its candidate index (0).
        assertEquals("C", result[2].slot.activityName)
    }

    @Test
    fun fallbackAtExactlyNowPlus24HoursAcrossDaylightSavingIsKept() {
        // Fall back on Nov 1: 24 real hours after Sat Oct 31 20:00 CDT is Sun Nov 1 19:00 CST.
        val now = at(10, 31, 20)
        val result = today(now, emptyList())
        assertEquals(listOf(at(11, 1, 8), at(11, 1, 12), at(11, 1, 19)), result.map { it.slot.start })
    }

    // Other rules

    @Test
    fun slotInTheSameHourAsAnExistingPlanIsSkipped() {
        val clash = today(friday(10), combos("Friday_Night"), existing = listOf(friday(21, 30)))
        assertTrue(usualStarts(clash).isEmpty())
        val noClash = today(friday(10), combos("Friday_Night"), existing = listOf(friday(22)))
        assertEquals(listOf(friday(21)), usualStarts(noClash))
    }

    @Test
    fun maxCountKeepsTheEarliestThree() {
        val result = today(
            friday(6),
            combos("Saturday_Wake Up", "Friday_Night", "Friday_Wake Up", "Friday_Evening", "Friday_Afternoon"),
        )
        assertEquals(listOf(friday(7), friday(12), friday(17)), result.map { it.slot.start })
        assertTrue(result.all { it.source == OpenSlotSource.USUAL })
    }

    @Test
    fun unknownCombosAreIgnored() {
        val result = today(friday(10), combos("Funday_Night", "Friday_Brunch", "Friday_Night"))
        assertEquals(listOf(friday(21)), usualStarts(result))
    }

    @Test
    fun spontaneousActivitiesComeFirstForToday() {
        val ordered = OpenSlotGenerator.activities(
            listOf("Hiking", "Tacos", "My Custom Thing", "Basketball", "Board Games"),
            ActivitySuggestionTier.SPONTANEOUS,
        )
        assertEquals(listOf("Tacos", "My Custom Thing", "Board Games", "Hiking", "Basketball"), ordered)
    }

    @Test
    fun nextUsualOccurrenceIgnoresLeadTime() {
        val next = OpenSlotGenerator.nextUsualOccurrence(
            combos("Monday_Night", "Friday_Evening"), friday(16, 55), friday(16, 55).plusSeconds(7 * 86_400), zone,
        )!!
        assertEquals(friday(17), next.start)
        assertEquals(DayOfWeek.FRIDAY, next.dayOfWeek)
        assertEquals(TimeSlot.EVENING, next.timeSlot)
    }

    @Test
    fun nextUsualOccurrenceIsNullWithoutCombos() {
        assertNull(OpenSlotGenerator.nextUsualOccurrence(emptyList(), friday(10), friday(10).plusSeconds(86_400), zone))
    }
}
