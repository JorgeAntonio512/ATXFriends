package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.NotificationPreferences
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.ui.settings.fieldValueElements
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins every `users/{uid}` write Settings makes: field-level only, iOS field names, iOS value
 * types (Double radius, String "Day_Slot" combos, Bool arrays, Timestamps, GeoPoint).
 */
class UserWritesTest {

    private val now = Instant.ofEpochSecond(1_770_000_000, 500_000_000)
    private val ts = Timestamp(1_770_000_000, 500_000_000)

    @Test
    fun displayName_writesNameAndUpdatedAtOnly() {
        val f = UserWrites.displayName("Sam", now).fields
        assertEquals(listOf("displayName", "updatedAt"), f.keys.toList())
        assertEquals("Sam", f["displayName"])
        assertEquals(ts, f["updatedAt"])
    }

    @Test
    fun radius_isADouble() {
        val f = UserWrites.radius(15, now).fields
        assertEquals(listOf("radiusMiles", "updatedAt"), f.keys.toList())
        assertEquals(15.0, f["radiusMiles"])
        assertTrue(f["radiusMiles"] is Double)
    }

    @Test
    fun activities_areThreeParallelArrays() {
        val list = listOf(
            ProfileActivity("a1", "Hiking", true),
            ProfileActivity("a2", "Tacos", true),
            ProfileActivity("a3", "Yoga", true),
            ProfileActivity("a4", "Chess", false),
        )
        val f = UserWrites.activities(list, now).fields
        assertEquals(listOf("activityIDs", "activityNames", "activityIsPrimary", "updatedAt"), f.keys.toList())
        assertEquals(listOf("a1", "a2", "a3", "a4"), f["activityIDs"])
        assertEquals(listOf("Hiking", "Tacos", "Yoga", "Chess"), f["activityNames"])
        assertEquals(listOf(true, true, true, false), f["activityIsPrimary"])
        assertEquals(ts, f["updatedAt"])
    }

    @Test
    fun daySlotCombos_areDayUnderscoreSlotStrings_withTheSlotSpaceKept() {
        val combos = listOf(DaySlotCombo("Saturday_Wake Up"), DaySlotCombo("Monday_Night"), DaySlotCombo("Sunday_Owl Hours"))
        val f = UserWrites.daySlotCombos(combos, now).fields
        assertEquals(listOf("daySlotCombos", "updatedAt"), f.keys.toList())
        assertEquals(listOf("Saturday_Wake Up", "Monday_Night", "Sunday_Owl Hours"), f["daySlotCombos"])
    }

    @Test
    fun photoURLs_writesTheWholeArray() {
        val f = UserWrites.photoURLs(listOf("u0", "u1", "u2"), now).fields
        assertEquals(listOf("photoURLs", "updatedAt"), f.keys.toList())
        assertEquals(listOf("u0", "u1", "u2"), f["photoURLs"])
    }

    @Test
    fun notificationPreferences_allFiveKeys_includingTheHiddenGroupUpdates() {
        val prefs = NotificationPreferences(newMatches = false, newMessages = true, planRequests = false, planConfirmations = true, groupUpdates = false)
        val f = UserWrites.notificationPreferences(prefs, now).fields
        assertEquals(
            listOf(
                "notificationPreferences.newMatches",
                "notificationPreferences.newMessages",
                "notificationPreferences.planRequests",
                "notificationPreferences.planConfirmations",
                "notificationPreferences.groupUpdates",
                "updatedAt",
            ),
            f.keys.toList(),
        )
        assertEquals(listOf(false, true, false, true, false), f.values.take(5))
        assertEquals(ts, f["updatedAt"])
    }

    @Test
    fun locationSharing_off_writesOnlyTheMode() {
        val f = UserWrites.locationSharing(LocationSharingMode.OFF, null, now).fields
        assertEquals(mapOf("locationSharingMode" to "off"), f)
    }

    @Test
    fun locationSharing_withFix_writesSnappedCoordinateGeoPointAndTimestamp_butNoUpdatedAt() {
        val f = UserWrites.locationSharing(LocationSharingMode.ON_OPEN, Coordinate(30.26719, -97.74306), now).fields
        assertEquals(listOf("locationSharingMode", "latitude", "longitude", "location", "locationUpdatedAt"), f.keys.toList())
        assertEquals("onOpen", f["locationSharingMode"])
        assertEquals(30.27, f["latitude"])
        assertEquals(-97.74, f["longitude"])
        assertEquals(GeoPoint(30.27, -97.74), f["location"])
        assertEquals(ts, f["locationUpdatedAt"])
    }

    @Test
    fun locationSharing_modeRawValues() {
        assertEquals("once", UserWrites.locationSharing(LocationSharingMode.ONCE, null, now).fields["locationSharingMode"])
        assertEquals("onOpen", UserWrites.locationSharing(LocationSharingMode.ON_OPEN, null, now).fields["locationSharingMode"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun locationSharing_refusesUnknownMode() {
        UserWrites.locationSharing(LocationSharingMode.UNKNOWN, null, now)
    }

    @Test
    fun blockAndUnblock_areArrayUnionAndArrayRemove_ofBlockedUsers() {
        val block = UserWrites.block("other").fields
        assertEquals(listOf("blockedUsers"), block.keys.toList())
        assertTrue(block["blockedUsers"] is FieldValue)
        assertTrue(block["blockedUsers"]!!.javaClass.simpleName.contains("Union"))
        assertEquals(listOf("other"), fieldValueElements(block["blockedUsers"]))

        val unblock = UserWrites.unblock("other").fields
        assertEquals(listOf("blockedUsers"), unblock.keys.toList())
        assertTrue(unblock["blockedUsers"]!!.javaClass.simpleName.contains("Remove"))
        assertEquals(listOf("other"), fieldValueElements(unblock["blockedUsers"]))
    }
}
