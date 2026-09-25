package com.georgeappdev.atxfriends.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CoarseLocationTest {

    @Test
    fun snap_roundsToTwoDecimals() {
        assertEquals(Coordinate(30.27, -97.74), CoarseLocation.snap(Coordinate(30.26719, -97.74306)))
    }

    @Test
    fun snap_roundsHalvesAwayFromZero_likeSwift_evenForNegatives() {
        assertEquals(-97.75, CoarseLocation.snap(Coordinate(0.0, -97.745)).longitude, 1e-9)
        assertEquals(30.27, CoarseLocation.snap(Coordinate(30.265, 0.0)).latitude, 1e-9)
    }

    private val now = Instant.ofEpochSecond(1_770_000_000)
    private val home = Coordinate(30.27, -97.74)

    @Test
    fun onOpen_throttledUnderFifteenMinutes() {
        val far = Coordinate(30.40, -97.74)
        assertEquals(OnOpenThrottle.Decision.THROTTLED, OnOpenThrottle.decide(now, far, home, now.minusSeconds(14 * 60)))
        assertEquals(OnOpenThrottle.Decision.WRITE, OnOpenThrottle.decide(now, far, home, now.minusSeconds(16 * 60)))
    }

    @Test
    fun onOpen_skippedUnderHalfAMile() {
        val nearby = Coordinate(30.275, -97.74) // ~0.35 mi north
        val moved = Coordinate(30.28, -97.74) // ~0.69 mi north
        val old = now.minusSeconds(3600)
        assertEquals(OnOpenThrottle.Decision.NOT_MOVED, OnOpenThrottle.decide(now, nearby, home, old))
        assertEquals(OnOpenThrottle.Decision.WRITE, OnOpenThrottle.decide(now, moved, home, old))
    }

    @Test
    fun onOpen_writesWhenNothingStoredYet() {
        assertEquals(OnOpenThrottle.Decision.WRITE, OnOpenThrottle.decide(now, home, null, null))
        assertNull(OnOpenThrottle.storedCoordinate(0.0, 0.0))
    }

    @Test
    fun lastUpdatedLabel() {
        val zone = ZoneId.of("America/Chicago")
        val noon = Instant.parse("2026-09-25T17:00:00Z") // 12:00 in Austin
        assertEquals(LastUpdated.TODAY, LastUpdated.of(noon.minusSeconds(3600), noon, zone))
        assertEquals(LastUpdated.THIS_WEEK, LastUpdated.of(noon.minusSeconds(13 * 3600), noon, zone))
        assertEquals(LastUpdated.THIS_WEEK, LastUpdated.of(noon.minusSeconds(6 * 86400), noon, zone))
        assertEquals(LastUpdated.OVER_A_WEEK_AGO, LastUpdated.of(noon.minusSeconds(7 * 86400), noon, zone))
    }
}
