package com.georgeappdev.atxfriends.domain.matching

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.UserProfile

/**
 * Port of iOS `MatchingService.shouldMatch` and helpers (spec §5.1). Two people match when
 * they share at least one activity — exactly, or through a shared category — AND at least
 * one day/slot combo.
 */
object MatchingRules {

    fun shouldMatch(user1: UserProfile, user2: UserProfile): Boolean {
        val activitiesCompatible = hasOverlappingActivities(user1, user2) ||
            sharedActivityCategories(user1, user2).isNotEmpty()
        return activitiesCompatible && hasOverlappingTimes(user1, user2)
    }

    /** By activity ID first, then by normalized name (legacy duplicates like "Hiking" / "hiking "). */
    fun hasOverlappingActivities(user1: UserProfile, user2: UserProfile): Boolean {
        val ids1 = user1.activities.map { it.id }.toSet()
        if (user2.activities.any { it.id in ids1 }) return true
        val names1 = user1.activities.map { normalizedForComparison(it.name) }.toSet()
        return user2.activities.any { normalizedForComparison(it.name) in names1 }
    }

    /**
     * Plain set intersection of day/slot combos. Only combos iOS can parse take part — iOS
     * drops unparseable strings when it reads a profile.
     */
    fun hasOverlappingTimes(user1: UserProfile, user2: UserProfile): Boolean {
        val combos1 = user1.daySlotCombos.knownPairs()
        return user2.daySlotCombos.knownPairs().any { it in combos1 }
    }

    /** Categories both users have an activity in. Custom activity names have no category. */
    fun sharedActivityCategories(user1: UserProfile, user2: UserProfile): Set<ActivityCategory> =
        categories(user1) intersect categories(user2)

    /** iOS `Activity.normalizedForComparison`: trim, collapse whitespace runs, lowercase. */
    fun normalizedForComparison(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase()

    private fun categories(user: UserProfile): Set<ActivityCategory> =
        user.activities.mapNotNull { ActivityCategories.category(it.name) }.toSet()

    private fun List<DaySlotCombo>.knownPairs() = filter { it.isKnown }.map { it.day to it.slot }.toSet()
}
