package com.georgeappdev.atxfriends.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.net.toUri
import com.georgeappdev.atxfriends.R
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

/**
 * iOS CalendarEventFields: what every calendar gets. There's no stored end time; iOS always
 * uses start + 2 hours.
 */
data class CalendarEventFields(val title: String, val start: Instant, val location: String?, val notes: String) {
    val end: Instant get() = start.plus(Duration.ofHours(2))
}

/**
 * Minimal Android side of iOS PlanCalendarActionHandler: directions and add-to-calendar
 * through the phone's own apps. Android's chooser plays the role of iOS's Apple/Google/Waze
 * and Apple/Google/Outlook menus. The calendar app shows its own "save" screen, so there's no
 * "Added ✓" tracking.
 */
object PlanExternalActions {

    /**
     * A `geo:` link: exact coordinates when the plan has them, otherwise the free-text
     * location as a search. Null when there's neither.
     */
    fun directionsUri(location: String?, latitude: Double?, longitude: Double?): String? = when {
        latitude != null && longitude != null -> "geo:$latitude,$longitude?q=$latitude,$longitude"
        !location.isNullOrBlank() -> "geo:0,0?q=" + URLEncoder.encode(location, "UTF-8").replace("+", "%20")
        else -> null
    }

    fun openDirections(context: Context, location: String?, latitude: Double?, longitude: Double?) {
        val uri = directionsUri(location, latitude, longitude) ?: return
        launch(context, Intent(Intent.ACTION_VIEW, uri.toUri()))
    }

    fun addToCalendar(context: Context, fields: CalendarEventFields) {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, fields.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, fields.start.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fields.end.toEpochMilli())
            .putExtra(CalendarContract.Events.DESCRIPTION, fields.notes)
        fields.location?.takeIf { it.isNotEmpty() }?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.plan_actions_no_app, Toast.LENGTH_SHORT).show()
        }
    }
}
