package com.georgeappdev.atxfriends.domain.profile

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ProfileViewModel.validateProfile and each setup step's Continue rule. */
class SetupRulesTest {

    private fun activities(main: Int, extra: Int) =
        (1..main).map { ProfileActivity("m$it", "Main $it", true) } + (1..extra).map { ProfileActivity("e$it", "Extra $it", false) }

    private val threeCombos = listOf("Monday_Night", "Saturday_Wake Up", "Sunday_Owl Hours").map(::DaySlotCombo)

    private fun problem(
        name: String = "Sam",
        photos: Int = 3,
        acts: List<ProfileActivity> = activities(3, 0),
        combos: List<DaySlotCombo> = threeCombos,
        lat: Double = 30.27,
        lng: Double = -97.74,
    ) = SetupRules.problem(name, photos, acts, combos, lat, lng)

    @Test
    fun aCompleteProfile_isValid() {
        assertNull(problem())
        assertNull(problem(acts = activities(3, 7)))
        assertNull(problem(combos = threeCombos + DaySlotCombo("Friday_Evening")))
    }

    @Test
    fun problems_inIosOrder() {
        assertEquals(SetupProblem.NAME, problem(name = "  ", photos = 0, acts = emptyList(), combos = emptyList(), lat = 0.0))
        assertEquals(SetupProblem.PHOTOS, problem(photos = 2, acts = emptyList()))
        assertEquals(SetupProblem.PHOTOS, problem(photos = 4))
        assertEquals(SetupProblem.ACTIVITY_COUNT, problem(acts = activities(2, 0)))
        assertEquals(SetupProblem.ACTIVITY_COUNT, problem(acts = activities(3, 8)))
        assertEquals(SetupProblem.MAIN_COUNT, problem(acts = activities(2, 2)))
        assertEquals(SetupProblem.MAIN_COUNT, problem(acts = activities(4, 0)))
        assertEquals(SetupProblem.TIME_SLOTS, problem(combos = threeCombos.take(2)))
        assertEquals(SetupProblem.LOCATION, problem(lat = 0.0))
        assertEquals(SetupProblem.LOCATION, problem(lng = 0.0))
    }

    @Test
    fun continueRules_perStep() {
        fun can(step: SetupStep, name: String = "Sam", photos: Int = 3, acts: List<ProfileActivity> = activities(3, 0), combos: List<DaySlotCombo> = threeCombos) =
            SetupRules.canContinue(step, name, photos, acts, combos)
        assertFalse(can(SetupStep.NAME, name = "S"))
        assertTrue(can(SetupStep.NAME, name = " Sa "))
        assertFalse(can(SetupStep.NAME, name = "x".repeat(31)))
        assertTrue(can(SetupStep.NAME, name = "x".repeat(30)))
        assertFalse(can(SetupStep.PHOTOS, photos = 2))
        assertTrue(can(SetupStep.PHOTOS, photos = 3))
        assertFalse(can(SetupStep.ACTIVITIES, acts = activities(2, 0)))
        assertTrue(can(SetupStep.ACTIVITIES, acts = activities(3, 1)))
        assertFalse(can(SetupStep.TIME_SLOTS, combos = threeCombos.take(2)))
        assertTrue(can(SetupStep.TIME_SLOTS))
    }

    @Test
    fun bio_isSilentlyCappedAt150Characters() {
        assertEquals("a".repeat(150), SetupRules.cappedBio("a".repeat(151)))
        assertEquals("short", SetupRules.cappedBio("short"))
        val emoji = "🌮".repeat(151)
        assertEquals(150, NameRules.characterCount(SetupRules.cappedBio(emoji)))
    }

    @Test
    fun steps_andPhotoPickerLimit() {
        assertEquals(listOf(1, 2, 3, 4, 5), SetupStep.entries.map { it.number })
        assertNull(SetupStep.NAME.previous)
        assertNull(SetupStep.COMPLETE.next)
        assertEquals(3, SetupRules.photosStillNeeded(0))
        assertEquals(1, SetupRules.photosStillNeeded(2))
        assertEquals(0, SetupRules.photosStillNeeded(3))
    }
}
