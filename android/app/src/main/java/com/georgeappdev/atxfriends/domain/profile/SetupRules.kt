package com.georgeappdev.atxfriends.domain.profile

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import java.text.BreakIterator

/** ProfileSetupFlowView.ProfileSetupStep, in order. The progress bar counts the first four. */
enum class SetupStep {
    NAME, PHOTOS, ACTIVITIES, TIME_SLOTS, COMPLETE;

    /** "Step n of 4". */
    val number: Int get() = ordinal + 1

    val previous: SetupStep? get() = entries.getOrNull(ordinal - 1)
    val next: SetupStep? get() = entries.getOrNull(ordinal + 1)

    companion object {
        const val PROGRESS_STEPS = 4
    }
}

/** Why the profile can't be saved yet — ProfileViewModel.validateProfile's messages. */
enum class SetupProblem { NAME, PHOTOS, ACTIVITY_COUNT, MAIN_COUNT, TIME_SLOTS, LOCATION }

/** The profile-setup rules that aren't already shared with Settings (see ProfileRules.kt). */
object SetupRules {
    /** NameInputView silently truncates the bio at 150 characters. */
    const val BIO_MAX = 150

    fun cappedBio(text: String): String {
        if (NameRules.characterCount(text) <= BIO_MAX) return text
        val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
        var end = 0
        repeat(BIO_MAX) { end = breaks.next() }
        return text.substring(0, end)
    }

    /** Each step's Continue: name 2–30, exactly 3 photos, 3+ activities, 3+ times. */
    fun canContinue(
        step: SetupStep,
        name: String,
        photoCount: Int,
        activities: List<ProfileActivity>,
        combos: List<DaySlotCombo>,
    ): Boolean = when (step) {
        SetupStep.NAME -> NameRules.isValid(name)
        SetupStep.PHOTOS -> photoCount == PhotoRules.SLOT_COUNT
        SetupStep.ACTIVITIES -> activities.size >= ActivitySelection.MIN_TOTAL
        SetupStep.TIME_SLOTS -> AvailabilityRules.isValid(combos)
        SetupStep.COMPLETE -> true
    }

    /**
     * ProfileViewModel.validateProfile, same checks in the same order. iOS treats a stored (0, 0)
     * coordinate (either half zero) as "no location".
     */
    fun problem(
        name: String,
        photoCount: Int,
        activities: List<ProfileActivity>,
        combos: List<DaySlotCombo>,
        latitude: Double,
        longitude: Double,
    ): SetupProblem? = when {
        name.trim(' ', '\t').isEmpty() -> SetupProblem.NAME
        photoCount != PhotoRules.SLOT_COUNT -> SetupProblem.PHOTOS
        activities.size !in ActivitySelection.MIN_TOTAL..ActivitySelection.MAX_TOTAL -> SetupProblem.ACTIVITY_COUNT
        ActivitySelection.mainCount(activities) != ActivitySelection.MAIN_COUNT -> SetupProblem.MAIN_COUNT
        !AvailabilityRules.isValid(combos) -> SetupProblem.TIME_SLOTS
        latitude == 0.0 || longitude == 0.0 -> SetupProblem.LOCATION
        else -> null
    }

    /** iOS `maxSelectionCount: 3 - selectedPhotos.count`. */
    fun photosStillNeeded(photoCount: Int): Int = (PhotoRules.SLOT_COUNT - photoCount).coerceAtLeast(0)
}
