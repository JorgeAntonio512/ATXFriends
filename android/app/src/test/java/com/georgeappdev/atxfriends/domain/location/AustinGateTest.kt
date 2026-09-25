package com.georgeappdev.atxfriends.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** LocationGateView.swift: ≤ 50.0 miles from (30.2672, -97.7431) passes, farther is waitlisted. */
class AustinGateTest {

    /** A point [miles] due north of Austin (a meridian is a great circle, so this is exact). */
    private fun northOfAustin(miles: Double): Coordinate {
        val degrees = miles * 1609.34 / 6_371_000.0 * 180 / PI
        return Coordinate(AustinGate.AUSTIN.latitude + degrees, AustinGate.AUSTIN.longitude)
    }

    @Test
    fun austinItself_isZeroMilesAndPasses() {
        assertEquals(0.0, AustinGate.milesFromAustin(AustinGate.AUSTIN), 1e-9)
        assertEquals(AustinGate.Outcome.PASS, AustinGate.evaluate(AustinGate.AUSTIN))
    }

    @Test
    fun justInside50Miles_passes() {
        val c = northOfAustin(49.99)
        assertEquals(49.99, AustinGate.milesFromAustin(c), 0.001)
        assertEquals(AustinGate.Outcome.PASS, AustinGate.evaluate(c))
    }

    @Test
    fun exactly50Miles_passes() {
        assertEquals(AustinGate.Outcome.PASS, AustinGate.evaluate(northOfAustin(50.0 - 1e-9)))
    }

    @Test
    fun justOutside50Miles_isWaitlisted() {
        val c = northOfAustin(50.01)
        assertTrue(AustinGate.milesFromAustin(c) > 50.0)
        assertEquals(AustinGate.Outcome.WAITLIST, AustinGate.evaluate(c))
    }

    @Test
    fun realPlaces() {
        // Round Rock (~17 mi), San Marcos (~28 mi): in. San Antonio (~74 mi), New York: out.
        assertEquals(AustinGate.Outcome.PASS, AustinGate.evaluate(Coordinate(30.5083, -97.6789)))
        assertEquals(AustinGate.Outcome.PASS, AustinGate.evaluate(Coordinate(29.8833, -97.9414)))
        assertEquals(AustinGate.Outcome.WAITLIST, AustinGate.evaluate(Coordinate(29.4241, -98.4936)))
        assertEquals(AustinGate.Outcome.WAITLIST, AustinGate.evaluate(Coordinate(40.7128, -74.0060)))
    }

    @Test
    fun usesTheIosMilesDivisor() {
        // 1 degree of latitude on a 6,371 km sphere ≈ 111,195 m ≈ 69.09 mi at 1609.34 m/mi.
        val c = Coordinate(AustinGate.AUSTIN.latitude + 1, AustinGate.AUSTIN.longitude)
        assertEquals(111_194.93 / 1609.34, AustinGate.milesFromAustin(c), 0.01)
    }
}
