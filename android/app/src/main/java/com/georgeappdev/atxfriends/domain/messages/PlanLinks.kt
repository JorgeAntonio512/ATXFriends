package com.georgeappdev.atxfriends.domain.messages

import com.georgeappdev.atxfriends.data.model.Plan
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Directions to a plan's place as a `geo:` URI, which every Android maps app (Google Maps, Waze,
 * and others) can open, so the system chooser stands in for iOS's Apple / Google / Waze menu.
 * Like iOS (NavigationApp.directionsURL), exact coordinates win when the plan has them; plans
 * without them fall back to searching the free-text location.
 */
object PlanDirections {
    fun geoUri(plan: Plan): String? {
        val lat = plan.locationLatitude
        val lng = plan.locationLongitude
        if (lat != null && lng != null) {
            val point = "${coordinate(lat)},${coordinate(lng)}"
            val label = plan.locationName?.takeIf { it.isNotBlank() } ?: plan.location?.takeIf { it.isNotBlank() }
            // The q= form drops a pin, labeled with the place name, on the exact spot.
            return "geo:$point?q=$point" + (label?.let { "(${encode(it)})" } ?: "")
        }
        val query = plan.location?.takeIf { it.isNotBlank() } ?: return null
        return "geo:0,0?q=${encode(query)}"
    }

    private fun coordinate(value: Double) = String.format(Locale.US, "%.6f", value)

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/**
 * The one event shape every iOS calendar provider gets (CalendarEventFields, 1-on-1 init): the
 * activity as the title, the confirmed time as the start, always two hours long, the plan's
 * free-text `location`, and "Hanging out with {name}" as notes.
 */
data class PlanCalendarEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val location: String?,
    val notes: String,
) {
    companion object {
        val LENGTH: Duration = Duration.ofHours(2)

        /**
         * iOS starts at `confirmedDate ?? Date()`. Android also falls back to the time a legacy
         * reschedule request stands at (the original proposed time) before falling back to now.
         */
        fun of(plan: Plan, otherUserName: String, now: Instant): PlanCalendarEvent {
            val start = plan.confirmedDate ?: plan.standingDate ?: now
            return PlanCalendarEvent(
                title = plan.activity.name,
                start = start,
                end = start.plus(LENGTH),
                location = plan.location,
                notes = "Hanging out with $otherUserName",
            )
        }
    }
}
