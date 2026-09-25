package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.at
import com.georgeappdev.atxfriends.domain.messages.MessageFixtures.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class PlanLinksTest {

    @Test
    fun directions_useExactCoordinates_labeledWithThePlaceName() {
        val p = plan(location = "3825 Lake Austin Blvd", locationName = "Mozart's Coffee")
            .copy(locationLatitude = 30.295, locationLongitude = -97.784)
        assertEquals("geo:30.295000,-97.784000?q=30.295000,-97.784000(Mozart%27s%20Coffee)", PlanDirections.geoUri(p))
    }

    @Test
    fun directions_withCoordinatesButNoName_fallBackToTheLocationText_thenNoLabel() {
        val base = plan(location = "Zilker Park").copy(locationLatitude = 30.2669, locationLongitude = -97.7729)
        assertEquals("geo:30.266900,-97.772900?q=30.266900,-97.772900(Zilker%20Park)", PlanDirections.geoUri(base))
        assertEquals("geo:30.266900,-97.772900?q=30.266900,-97.772900", PlanDirections.geoUri(base.copy(location = null)))
    }

    @Test
    fun directions_withoutCoordinates_searchTheFreeText_likeIosFallback() {
        assertEquals("geo:0,0?q=1100%20S%20Lamar%20%26%20Barton", PlanDirections.geoUri(plan(location = "1100 S Lamar & Barton")))
    }

    @Test
    fun directions_withNoPlace_isNull() {
        assertNull(PlanDirections.geoUri(plan(location = null)))
        assertNull(PlanDirections.geoUri(plan(location = "  ")))
    }

    @Test
    fun calendarEvent_hasIosFields_twoHoursLong() {
        val start = at(day = 26, hour = 19)
        val event = PlanCalendarEvent.of(plan(activity = "Poker", confirmedDate = start, location = "Mozart's"), "Sam", now = at())
        assertEquals(PlanCalendarEvent("Poker", start, start.plus(Duration.ofHours(2)), "Mozart's", "Hanging out with Sam"), event)
    }

    @Test
    fun calendarEvent_withAPendingReschedule_usesTheTimeThatStillStands() {
        val original = at(day = 26, hour = 19)
        val p = plan(status = PlanStatus.COUNTER_PROPOSED, confirmedDate = original, counterDate = at(day = 27))
        assertEquals(original, PlanCalendarEvent.of(p, "Sam", now = at()).start)
    }

    @Test
    fun calendarEvent_withNoLocation_leavesItOut() {
        assertNull(PlanCalendarEvent.of(plan(location = null), "Sam", now = at()).location)
    }
}
