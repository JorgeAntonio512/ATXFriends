package com.georgeappdev.atxfriends.ui.messages

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.domain.messages.PlanCalendarEvent
import com.georgeappdev.atxfriends.domain.messages.PlanDirections

/**
 * Directions and Add to Calendar for a plan, done with the phone's own apps: a `geo:` link
 * through the system chooser (any maps app — Google Maps, Waze, …) and the calendar app's
 * new-event screen via CalendarContract, which needs no calendar permission. Returns the two
 * actions; shows an alert if the phone has no app for one.
 */
@Composable
fun rememberPlanLinks(
    calendarEvent: (Plan) -> PlanCalendarEvent,
    isAddedToCalendar: (planID: String) -> Boolean,
    onReturnedFromCalendar: (planID: String) -> Unit,
): PlanLinks {
    val context = LocalContext.current
    val currentEvent by rememberUpdatedState(calendarEvent)
    val currentIsAdded by rememberUpdatedState(isAddedToCalendar)
    val currentOnReturned by rememberUpdatedState(onReturnedFromCalendar)
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    var calendarPlanID by rememberSaveable { mutableStateOf<String?>(null) }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        calendarPlanID?.let(currentOnReturned)
        calendarPlanID = null
    }

    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    return remember(context, calendarLauncher) {
        PlanLinks(
            directions = { plan ->
                val uri = PlanDirections.geoUri(plan)
                val chooser = uri?.let { PlanIntents.directions(it, context.getString(R.string.plan_get_directions)) }
                if (chooser == null || !context.tryStart(chooser)) problem = R.string.plan_no_maps_app
            },
            addToCalendar = { plan ->
                val event = currentEvent(plan)
                if (currentIsAdded(plan.id)) {
                    // Already added: open the calendar at the plan's time, as iOS opens its calendar app.
                    if (!context.tryStart(PlanIntents.viewCalendar(event))) problem = R.string.plan_no_calendar_app
                } else {
                    calendarPlanID = plan.id
                    try {
                        calendarLauncher.launch(PlanIntents.insertEvent(event))
                    } catch (e: ActivityNotFoundException) {
                        calendarPlanID = null
                        problem = R.string.plan_no_calendar_app
                    }
                }
            },
        )
    }
}

class PlanLinks(val directions: (Plan) -> Unit, val addToCalendar: (Plan) -> Unit)

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** The intents themselves. */
object PlanIntents {
    fun directions(geoUri: String, chooserTitle: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)), chooserTitle)

    /** The calendar app's new-event screen, filled in with iOS's event fields. */
    fun insertEvent(event: PlanCalendarEvent): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, event.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.start.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
            .putExtra(CalendarContract.Events.DESCRIPTION, event.notes)
            .apply { event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) } }

    /** The calendar app, opened at the event's day. */
    fun viewCalendar(event: PlanCalendarEvent): Intent {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").let {
            ContentUris.appendId(it, event.start.toEpochMilli())
        }.build()
        return Intent(Intent.ACTION_VIEW, uri)
    }
}

