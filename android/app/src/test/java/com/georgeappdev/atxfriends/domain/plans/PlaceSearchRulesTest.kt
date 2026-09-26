package com.georgeappdev.atxfriends.domain.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceSearchRulesTest {

    private fun place(id: String, miles: Double?) = PlaceSuggestion(id, "Place $id", null, null, null, miles)

    @Test
    fun nearestFirst_sortsOnTheDevice_notByTheSearchOrder() {
        val relevanceOrder = listOf(place("far", 9.0), place("near", 0.4), place("mid", 3.2))
        assertEquals(listOf("near", "mid", "far"), PlaceSearchRules.nearestFirst(relevanceOrder).map { it.id })
    }

    @Test
    fun nearestFirst_unknownDistancesGoLast_inTheirOriginalOrder() {
        val places = listOf(place("x", null), place("a", 2.0), place("y", null), place("b", 1.0))
        assertEquals(listOf("b", "a", "x", "y"), PlaceSearchRules.nearestFirst(places).map { it.id })
    }

    @Test
    fun nearestFirst_keepsAtMostFive_theNearestOnes() {
        val places = (8 downTo 1).map { place("p$it", it.toDouble()) }
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), PlaceSearchRules.nearestFirst(places).map { it.id })
    }

    @Test
    fun origin_ignoresMissingAndTheZeroZeroUnsetValue() {
        assertNull(PlaceSearchRules.origin(null, -97.74))
        assertNull(PlaceSearchRules.origin(0.0, 0.0))
        assertEquals(PlaceCoordinate(30.27, -97.74), PlaceSearchRules.origin(30.27, -97.74))
    }

    @Test
    fun biasBox_isAbout20KmSquare_centeredOnTheUser() {
        val (sw, ne) = PlaceSearchRules.biasBox(PlaceCoordinate(30.27, -97.74))
        assertEquals(30.27, (sw.latitude + ne.latitude) / 2, 1e-9)
        assertEquals(-97.74, (sw.longitude + ne.longitude) / 2, 1e-9)
        assertEquals(20.0, (ne.latitude - sw.latitude) * 111.32, 0.01)
        // Longitude degrees are shorter at Austin's latitude, so the box is wider in degrees.
        assertEquals(20.0, (ne.longitude - sw.longitude) * 111.32 * Math.cos(Math.toRadians(30.27)), 0.01)
    }

    @Test
    fun miles_fromMeters() {
        assertEquals(1.0, PlaceSearchRules.miles(1609)!!, 0.001)
        assertNull(PlaceSearchRules.miles(null))
    }

    @Test
    fun shortAddress_isStreetAndCity_likeIos() {
        assertEquals("1120 S Lamar Blvd, Austin", PlaceSearchRules.shortAddress("1120 S Lamar Blvd, Austin, TX, USA"))
        assertEquals("W Anderson Ln, Austin", PlaceSearchRules.shortAddress("W Anderson Ln, Austin, TX 78757, USA"))
        assertEquals("Austin", PlaceSearchRules.shortAddress("Austin, TX, USA"))
        assertEquals("Round Rock", PlaceSearchRules.shortAddress("Round Rock, TX"))
        assertNull(PlaceSearchRules.shortAddress(""))
        assertNull(PlaceSearchRules.shortAddress(null))
    }
}
