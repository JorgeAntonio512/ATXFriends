package com.georgeappdev.atxfriends.domain.distance

import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class DistanceDisplayTest {

    @Test
    fun bucketEdgesMatchIosExactly() {
        val expected = listOf(
            0.0 to "Under 1 mi away",
            0.9999 to "Under 1 mi away",
            1.0 to "~2 mi away",
            2.9999 to "~2 mi away",
            3.0 to "~5 mi away",
            6.9999 to "~5 mi away",
            7.0 to "~10 mi away",
            11.9999 to "~10 mi away",
            12.0 to "~15 mi away",
            19.9999 to "~15 mi away",
            20.0 to "~25 mi away",
            34.9999 to "~25 mi away",
            35.0 to "~50 mi away",
            59.9999 to "~50 mi away",
            60.0 to "50+ mi away",
            500.0 to "50+ mi away",
        )
        for ((miles, label) in expected) assertEquals("$miles mi", label, DistanceDisplay.bucketLabel(miles))
    }

    @Test
    fun hiddenWhenNotSharingOrNoDistance() {
        assertNull(DistanceDisplay.label(5.0, isSharing = false))
        assertNull(DistanceDisplay.label(null, isSharing = true))
        assertEquals("~5 mi away", DistanceDisplay.label(5.0, isSharing = true))
    }

    private val me = UserProfile.fromFirestore("me", TestDocs.user())!!.copy(latitude = 30.27, longitude = -97.74)

    @Test
    fun betweenUsers_respectsTheOtherPersonsSharingMode() {
        val near = me.copy(id = "them", latitude = 30.28, longitude = -97.74) // ~0.69 mi
        assertEquals("Under 1 mi away", DistanceDisplay.between(me, near.copy(locationSharingMode = LocationSharingMode.ON_OPEN)))
        assertEquals("Under 1 mi away", DistanceDisplay.between(me, near.copy(locationSharingMode = LocationSharingMode.ONCE)))
        assertNull(DistanceDisplay.between(me, near.copy(locationSharingMode = LocationSharingMode.OFF)))
        assertNull(DistanceDisplay.between(me, near.copy(locationSharingMode = LocationSharingMode.UNKNOWN)))
        // My own sharing mode doesn't matter — only whether the other person shares.
        assertEquals("Under 1 mi away", DistanceDisplay.between(me.copy(locationSharingMode = LocationSharingMode.OFF), near.copy(locationSharingMode = LocationSharingMode.ONCE)))
    }

    @Test
    fun hiddenWhenEitherPersonHasNoLocation() {
        val them = me.copy(id = "them", locationSharingMode = LocationSharingMode.ONCE)
        assertNull(DistanceDisplay.between(me.copy(latitude = 0.0, longitude = 0.0), them))
        assertNull(DistanceDisplay.between(me, them.copy(latitude = 0.0, longitude = 0.0)))
    }

    /** Reference values measured from iOS CLLocation.distance(from:) on this Mac. */
    @Test
    fun ellipsoidalDistanceAgreesWithCoreLocation() {
        fun close(expected: Double, actual: Double, tolerance: Double) =
            assertEquals(expected, actual, tolerance)
        close(1108.570901, GeoDistance.meters(30.27, -97.74, 30.28, -97.74), 0.001)     // due north
        close(111319.490793, GeoDistance.meters(0.0, 0.0, 0.0, 1.0), 0.001)             // along the equator
        close(962.191431, GeoDistance.meters(30.27, -97.74, 30.27, -97.75), 0.1)        // due east
        close(292269.947513, GeoDistance.meters(30.27, -97.74, 32.78, -96.80), 0.01)    // Austin → Dallas
        // Diagonal lines differ from CoreLocation by up to ~0.2% (Apple's formula isn't public).
        val ios = 19726.842027
        val ours = GeoDistance.meters(30.27, -97.74, 30.40, -97.60)
        assert(abs(ours - ios) / ios < 0.002)
        assertEquals(0.0, GeoDistance.meters(30.27, -97.74, 30.27, -97.74), 0.0)
    }
}
