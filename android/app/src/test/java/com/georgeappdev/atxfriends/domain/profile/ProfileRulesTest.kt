package com.georgeappdev.atxfriends.domain.profile

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    // Display name: 2–30 characters after trimming.

    @Test
    fun name_limits() {
        assertFalse(NameRules.isValid(""))
        assertFalse(NameRules.isValid(" A "))
        assertTrue(NameRules.isValid("Al"))
        assertTrue(NameRules.isValid("  Al  "))
        assertTrue(NameRules.isValid("x".repeat(30)))
        assertFalse(NameRules.isValid("x".repeat(31)))
    }

    @Test
    fun name_countsCharactersLikeSwift_soAnEmojiIsOne() {
        assertEquals(3, NameRules.characterCount("Jo👋"))
        assertTrue(NameRules.isValid("👋🏽👋🏽"))
    }

    // Activities: 3 Main + up to 7 Extra.

    private fun ids(list: List<ProfileActivity>) = list.map { it.id to it.isPrimary }

    private fun picks(n: Int): List<ProfileActivity> =
        (1..n).fold(emptyList()) { acc, i -> ActivitySelection.select(acc, "a$i", "Activity $i") }

    @Test
    fun firstThreePicksAreMain_restAreExtra() {
        assertEquals(
            listOf("a1" to true, "a2" to true, "a3" to true, "a4" to false, "a5" to false),
            ids(picks(5)),
        )
    }

    @Test
    fun validity_needsThreeToTen_withExactlyThreeMain() {
        assertFalse(ActivitySelection.isValid(picks(2)))
        assertTrue(ActivitySelection.isValid(picks(3)))
        assertTrue(ActivitySelection.isValid(picks(10)))
        val allMain = picks(4).map { it.copy(isPrimary = true) }
        assertFalse("4 Mains is invalid", ActivitySelection.isValid(allMain))
    }

    @Test
    fun capIsTen_andDuplicatesAreIgnored() {
        val ten = picks(10)
        assertEquals(ten, ActivitySelection.select(ten, "a11", "Eleven"))
        assertFalse(ActivitySelection.canAddMore(ten))
        assertEquals(picks(3), ActivitySelection.select(picks(3), "a2", "Activity 2"))
        assertEquals(7, ActivitySelection.MAX_EXTRAS)
    }

    @Test
    fun removingAMain_promotesTheFirstExtra() {
        val after = ActivitySelection.deselect(picks(5), "a2")
        assertEquals(listOf("a1" to true, "a3" to true, "a4" to true, "a5" to false), ids(after))
    }

    @Test
    fun removingDownToTwo_leavesTwoMains_andIsInvalid() {
        val after = ActivitySelection.deselect(picks(3), "a1")
        assertEquals(listOf("a2" to true, "a3" to true), ids(after))
        assertFalse(ActivitySelection.isValid(after))
        assertEquals(2, ActivitySelection.mainCount(after))
    }

    @Test
    fun starringAnExtra_swapsWithTheLastMain() {
        val after = ActivitySelection.toggleMain(picks(5), "a5")
        assertEquals(listOf("a1" to true, "a2" to true, "a3" to false, "a4" to false, "a5" to true), ids(after))
        assertEquals("starring a Main does nothing", after, ActivitySelection.toggleMain(after, "a1"))
    }

    @Test
    fun customNames_areCleanedAndComparedLikeIos() {
        assertEquals("Rock Climbing", ActivitySelection.cleanedName("  Rock \t  Climbing \n"))
        assertEquals("rock climbing", ActivitySelection.normalizedForComparison(" ROCK   climbing"))
        assertEquals("BJJ", ActivitySelection.cleanedName(" BJJ "))
    }

    @Test
    fun availableList_filtersSelectedAndSearch_caseInsensitive() {
        val catalog = listOf("a1" to "Hiking", "a2" to "Hot Yoga", "a3" to "Tacos")
        val selected = listOf(ProfileActivity("a1", "Hiking", true))
        val result = ActivitySelection.available(catalog, selected, "h", { it.first }, { it.second })
        assertEquals(listOf("a2" to "Hot Yoga"), result)
        assertTrue(ActivitySelection.hasExactMatch(catalog, " tacos ") { it.second })
        assertFalse(ActivitySelection.hasExactMatch(catalog, "taco") { it.second })
    }

    // Availability: at least 3, no maximum.

    @Test
    fun availability_minimumThree_noMaximum() {
        var combos = emptyList<DaySlotCombo>()
        combos = AvailabilityRules.toggle(combos, DayOfWeek.MONDAY, TimeSlot.NIGHT)
        combos = AvailabilityRules.toggle(combos, DayOfWeek.SATURDAY, TimeSlot.WAKE_UP)
        assertFalse(AvailabilityRules.isValid(combos))
        combos = AvailabilityRules.toggle(combos, DayOfWeek.SUNDAY, TimeSlot.OWL_HOURS)
        assertTrue(AvailabilityRules.isValid(combos))
        assertEquals(listOf("Monday_Night", "Saturday_Wake Up", "Sunday_Owl Hours"), combos.map { it.raw })

        val everything = AvailabilityRules.days.flatMap { d -> AvailabilityRules.slots.map { AvailabilityRules.combo(d, it) } }
        assertEquals(35, everything.size)
        assertTrue(AvailabilityRules.isValid(everything))
    }

    @Test
    fun availability_toggleRemoves_andUnknownCombosDontCount() {
        val combos = listOf(DaySlotCombo("Monday_Night"), DaySlotCombo("Funday_Brunch"), DaySlotCombo("Friday_Evening"))
        assertEquals(2, AvailabilityRules.count(combos))
        assertFalse(AvailabilityRules.isValid(combos))
        val removed = AvailabilityRules.toggle(combos, DayOfWeek.MONDAY, TimeSlot.NIGHT)
        assertEquals(listOf("Funday_Brunch", "Friday_Evening"), removed.map { it.raw })
    }

    @Test
    fun slotDisplayHelpers_matchTimeSlotSwift() {
        assertEquals("☀️", TimeSlot.WAKE_UP.icon)
        assertEquals("9:00pm – 2:00am", TimeSlot.NIGHT.timeRange)
        assertEquals("Saturday Wake Up", DaySlotCombo("Saturday_Wake Up").displayName)
    }

    @Test
    fun radiusSlider_clampsToFiveToTwentyFive() {
        assertEquals(10, RadiusRules.clampToSlider(10.0))
        assertEquals(5, RadiusRules.clampToSlider(1.0))
        assertEquals(25, RadiusRules.clampToSlider(50.0))
    }

    @Test
    fun cleanedName_collapsesEveryKindOfWhitespace_likeIos_withoutARegex() {
        // Names as they arrive from iPhone keyboards and pastes: tabs, newlines, no-break and
        // em spaces between and around the words.
        assertEquals("Rock Climbing", ActivitySelection.cleanedName("  Rock\u00A0\u00A0Climbing\n"))
        assertEquals("Board Games", ActivitySelection.cleanedName("Board\t\u2003 Games"))
        assertEquals("Tacos", ActivitySelection.cleanedName("\u00A0Tacos\u3000"))
        assertEquals("Día de Campo", ActivitySelection.cleanedName("Día  de\r\nCampo"))
        assertEquals("", ActivitySelection.cleanedName(" \u00A0\t "))
        assertEquals("board games", ActivitySelection.normalizedForComparison("BOARD\u00A0 Games"))
    }
}
