package com.georgeappdev.atxfriends.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlanExternalActionsTest {

    @Test
    fun directions_preferStoredCoordinates() {
        assertEquals("geo:30.26,-97.77?q=30.26,-97.77", PlanExternalActions.directionsUri("Zilker Park", 30.26, -97.77))
    }

    @Test
    fun directions_fallBackToTheFreeTextLocation() {
        assertEquals("geo:0,0?q=Mozart%27s%20Coffee%20%26%20Co", PlanExternalActions.directionsUri("Mozart's Coffee & Co", null, null))
    }

    @Test
    fun directions_needSomething() {
        assertNull(PlanExternalActions.directionsUri(null, null, null))
        assertNull(PlanExternalActions.directionsUri("  ", 30.0, null))
    }

    @Test
    fun calendarEvent_endsTwoHoursLater() {
        val start = Instant.ofEpochSecond(1_790_000_000)
        assertEquals(start.plusSeconds(7200), CalendarEventFields("Chess", start, null, "Hosted by Hana. Invited: Bea.").end)
    }
}
