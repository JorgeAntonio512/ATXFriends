package com.georgeappdev.atxfriends.data.repository

import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Pins every sign-up write to what iOS writes (AuthViewModel, userToFirestoreData, WaitlistView). */
class SignupWritesTest {

    private val now = Instant.ofEpochSecond(1_770_000_000, 500_000_000)
    private val ts = Timestamp(1_770_000_000, 500_000_000)

    @Test
    fun newUser_hasExactlyTheIosFieldsAndDefaults() {
        val f = SignupWrites.newUser("Sam Rivera", Coordinate(30.26719, -97.74316), now).fields
        assertEquals(
            listOf(
                "displayName", "bio", "photoURLs", "activityIDs", "activityNames", "activityIsPrimary",
                "daySlotCombos", "location", "latitude", "longitude", "radiusMiles", "createdAt", "updatedAt",
                "isProfileComplete", "notificationPreferences", "blockedUsers", "locationSharingMode",
            ),
            f.keys.toList(),
        )
        assertEquals("Sam Rivera", f["displayName"])
        assertEquals("", f["bio"])
        for (k in listOf("photoURLs", "activityIDs", "activityNames", "activityIsPrimary", "daySlotCombos", "blockedUsers")) {
            assertEquals(k, emptyList<Any>(), f[k])
        }
        assertEquals(10.0, f["radiusMiles"])
        assertEquals(ts, f["createdAt"])
        assertEquals(ts, f["updatedAt"])
        assertEquals(false, f["isProfileComplete"])
        assertEquals("off", f["locationSharingMode"])
        assertEquals(
            mapOf("newMatches" to true, "newMessages" to true, "planRequests" to true, "planConfirmations" to true, "groupUpdates" to true),
            f["notificationPreferences"],
        )
        // No email (iOS never persists it), no locationUpdatedAt (nil on iOS).
        assertFalse("email" in f)
        assertFalse("locationUpdatedAt" in f)
    }

    @Test
    fun newUser_snapsTheGateCoordinateToTheCoarseGrid() {
        val f = SignupWrites.newUser("", Coordinate(30.26719, -97.74316), now).fields
        assertEquals(30.27, f["latitude"])
        assertEquals(-97.74, f["longitude"])
        val geo = f["location"] as GeoPoint
        assertEquals(30.27, geo.latitude, 0.0)
        assertEquals(-97.74, geo.longitude, 0.0)
    }

    @Test
    fun newUser_readsBackAsAnIncompleteProfile() {
        val profile = UserProfile.fromFirestore("uid", SignupWrites.newUser("", Coordinate(30.3, -97.7), now).fields)
        assertNotNull("iOS's decoder must accept the sign-up doc", profile)
        assertFalse(profile!!.isProfileComplete)
    }

    @Test
    fun waitlist_isExactlyTwoKeys_lowercasedAndTrimmed() {
        val f = SignupWrites.waitlist("  Sam@Example.COM ").fields
        assertEquals(listOf("email", "submittedAt"), f.keys.toList())
        assertEquals("sam@example.com", f["email"])
        assertTrue(f["submittedAt"] is FieldValue)
    }

    @Test
    fun completeProfile_writesSetupFieldsAndMarksComplete() {
        val activities = listOf(
            ProfileActivity("a1", "Hiking", true), ProfileActivity("a2", "Tacos", true),
            ProfileActivity("a3", "Yoga", true), ProfileActivity("a4", "Chess", false),
        )
        val combos = listOf(DaySlotCombo("Monday_Night"), DaySlotCombo("Saturday_Wake Up"), DaySlotCombo("Sunday_Owl Hours"))
        val f = SignupWrites.completeProfile("  Sam ", "Bio ", listOf("u0", "u1", "u2"), activities, combos, now).fields
        assertEquals(
            listOf(
                "displayName", "bio", "photoURLs", "activityIDs", "activityNames", "activityIsPrimary",
                "daySlotCombos", "updatedAt", "isProfileComplete",
            ),
            f.keys.toList(),
        )
        assertEquals("Sam", f["displayName"])
        assertEquals("Bio ", f["bio"])
        assertEquals(listOf("u0", "u1", "u2"), f["photoURLs"])
        assertEquals(listOf("a1", "a2", "a3", "a4"), f["activityIDs"])
        assertEquals(listOf("Hiking", "Tacos", "Yoga", "Chess"), f["activityNames"])
        assertEquals(listOf(true, true, true, false), f["activityIsPrimary"])
        assertEquals(listOf("Monday_Night", "Saturday_Wake Up", "Sunday_Owl Hours"), f["daySlotCombos"])
        assertEquals(ts, f["updatedAt"])
        assertEquals(true, f["isProfileComplete"])
    }

    @Test
    fun googleName_isNeverTheEmailAddress() {
        assertEquals("Sam Rivera", GoogleNames.initialDisplayName("Sam Rivera", "sam@gmail.com"))
        assertEquals("Sam Rivera", GoogleNames.initialDisplayName(null, " Sam Rivera "))
        assertEquals("Sam Rivera", GoogleNames.initialDisplayName("sam@gmail.com", "Sam Rivera"))
        assertEquals("", GoogleNames.initialDisplayName("sam@gmail.com", null))
        assertEquals("", GoogleNames.initialDisplayName(null, "  "))
    }
}
