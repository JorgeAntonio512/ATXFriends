package com.georgeappdev.atxfriends.domain.matching

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityCategoriesTest {

    @Test
    fun tableMatchesTheSwiftSource() {
        assertEquals(286, ActivityCategories.map.size)
        assertEquals(13, ActivityCategory.entries.size)
        assertEquals(ActivityCategory.OUTDOOR_AND_NATURE, ActivityCategories.category("Hiking"))
        assertEquals(ActivityCategory.SPORTS_AND_FITNESS, ActivityCategories.category("Football"))
        assertEquals(ActivityCategory.OUTDOOR_AND_NATURE, ActivityCategories.category("Rucking"))
        assertEquals(ActivityCategory.FOOD_AND_DRINK, ActivityCategories.category("Tacos"))
        assertEquals(ActivityCategory.ENTERTAINMENT_AND_SOCIAL, ActivityCategories.category("Pool/Billiards"))
        assertEquals(ActivityCategory.MUSIC_AND_PERFORMANCE, ActivityCategories.category("R&B"))
        assertEquals(ActivityCategory.UNIQUE_AND_NICHE, ActivityCategories.category("Medieval Combat"))
        assertEquals("Wellness & Self-Care", ActivityCategory.WELLNESS_AND_SELF_CARE.displayName)
        assertEquals("wellnessAndSelfCare", ActivityCategory.WELLNESS_AND_SELF_CARE.raw)
    }

    @Test
    fun lookupIsExactAndCustomActivitiesHaveNoCategory() {
        assertNull(ActivityCategories.category("hiking"))
        assertNull(ActivityCategories.category("Hiking "))
        assertNull(ActivityCategories.category("Underwater Basket Weaving"))
    }
}

class MatchingRulesTest {

    private val base = UserProfile.fromFirestore("me", TestDocs.user())!!

    private fun user(id: String, activities: List<Pair<String, String>>, combos: List<String>) = base.copy(
        id = id,
        activities = activities.map { (aid, name) -> ProfileActivity(aid, name, true) },
        daySlotCombos = combos.map(::DaySlotCombo),
    )

    @Test
    fun exactActivityById_andSharedTime() {
        val a = user("a", listOf("x1" to "Hiking"), listOf("Monday_Night"))
        val b = user("b", listOf("x1" to "Something renamed"), listOf("Monday_Night", "Friday_Evening"))
        assertTrue(MatchingRules.shouldMatch(a, b))
    }

    @Test
    fun exactActivityByNormalizedName() {
        val a = user("a", listOf("id1" to "  Board   Games "), listOf("Monday_Night"))
        val b = user("b", listOf("id2" to "board games"), listOf("Monday_Night"))
        assertTrue(MatchingRules.hasOverlappingActivities(a, b))
    }

    @Test
    fun categoryOnlyOverlapStillMatches() {
        val a = user("a", listOf("1" to "Basketball"), listOf("Saturday_Wake Up"))
        val b = user("b", listOf("2" to "Football"), listOf("Saturday_Wake Up"))
        assertFalse(MatchingRules.hasOverlappingActivities(a, b))
        assertEquals(setOf(ActivityCategory.SPORTS_AND_FITNESS), MatchingRules.sharedActivityCategories(a, b))
        assertTrue(MatchingRules.shouldMatch(a, b))
    }

    @Test
    fun customActivitiesNeverMatchByCategory() {
        val a = user("a", listOf("1" to "Competitive Napping"), listOf("Monday_Night"))
        val b = user("b", listOf("2" to "Extreme Ironing"), listOf("Monday_Night"))
        assertFalse(MatchingRules.shouldMatch(a, b))
    }

    @Test
    fun needsATimeOverlapToo_andUnparseableCombosDontCount() {
        val a = user("a", listOf("1" to "Hiking"), listOf("Monday_Night", "Funday_Night"))
        val b = user("b", listOf("1" to "Hiking"), listOf("Tuesday_Night", "Funday_Night"))
        assertFalse(MatchingRules.shouldMatch(a, b))
    }
}

class MatchSectioningTest {

    private val me = UserProfile.fromFirestore("me", TestDocs.user())!!.copy(
        activities = listOf(ProfileActivity("a1", "Hiking", true)),
        daySlotCombos = listOf(DaySlotCombo("Monday_Night")),
        blockedUsers = listOf("blocked"),
    )

    private fun friend(id: String, overlaps: Boolean = true) = me.copy(
        id = id,
        blockedUsers = emptyList(),
        daySlotCombos = listOf(DaySlotCombo(if (overlaps) "Monday_Night" else "Friday_Evening")),
    )

    private fun match(other: String, meFirst: Boolean = true, mine: Boolean? = null, theirs: Boolean? = null, mutual: Boolean = false): Match {
        val base = Match.fromFirestore("id-$other", TestDocs.match())!!
        return if (meFirst) base.copy(id = "id-$other", user1ID = "me", user2ID = other, user1Decision = mine, user2Decision = theirs, isMutualMatch = mutual)
        else base.copy(id = "id-$other", user1ID = other, user2ID = "me", user1Decision = theirs, user2Decision = mine, isMutualMatch = mutual)
    }

    @Test
    fun categorizesLikeIos_keepingFetchOrder() {
        val matches = listOf(
            match("p1"),                                        // pending
            match("p2", meFirst = false, theirs = true),        // pending (they said Yay)
            match("nay", theirs = false),                       // they said Nay → hidden
            match("waiting", mine = true),                      // I said Yay, waiting → neither section
            match("c1", mine = true, theirs = true, mutual = true),
            match("blocked"),                                   // blocked → hidden
            match("stale"),                                     // no longer overlaps → hidden
            match("noProfile"),                                 // profile didn't load → dropped from view
        )
        val profiles = listOf("p1", "p2", "nay", "waiting", "c1", "blocked").associateWith { friend(it) } +
            ("stale" to friend("stale", overlaps = false))

        val s = MatchSectioning.sections(matches, "me", me, profiles)
        assertEquals(listOf("id-p1", "id-p2", "id-noProfile"), s.pending.map { it.id })
        assertEquals(listOf("id-c1"), s.connected.map { it.id })
        assertEquals(listOf("id-p1", "id-p2"), MatchSectioning.entries(s.pending, "me", profiles).map { it.match.id })
    }

    @Test
    fun matchesThatArentMineAreDropped() {
        val stranger = match("x").copy(user1ID = "someone", user2ID = "else")
        assertTrue(MatchSectioning.withoutBlocked(listOf(stranger), "me", me).isEmpty())
    }

    @Test
    fun showUpMeterMatchesIos() {
        assertEquals("New", showUpMeter(0, 0))
        assertEquals("80% (4/5)", showUpMeter(4, 5))
        assertEquals("66% (2/3)", showUpMeter(2, 3)) // truncated, not rounded
        assertEquals("100% (3/3)", showUpMeter(3, 3))
        assertEquals("0% (0/2)", showUpMeter(0, 2))
    }
}
