package com.georgeappdev.atxfriends.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageDates
import com.georgeappdev.atxfriends.domain.messages.RescheduleTime
import com.georgeappdev.atxfriends.domain.messages.standingDate
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.Instant
import java.time.ZoneId

/** iOS's sheet toolbar: an optional leading and trailing text button around a centered title. */
@Composable
private fun SheetTopBar(title: String, leading: Pair<String, () -> Unit>?, trailing: Pair<String, () -> Unit>?) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp)) {
        leading?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(label, style = atxText(17.sp), color = colors.appPrimary)
            }
        }
        Text(
            title,
            style = atxText(17.sp, FontWeight.SemiBold),
            color = colors.primaryText,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        trailing?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(label, style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
            }
        }
    }
}

/**
 * Port of iOS ReschedulePlanSheet: "Suggest a new time" for a confirmed plan, any future date and
 * time, sent with "Suggest New Time". Material's date picker and time entry stand in for iOS's
 * graphical date-and-time picker; days before today can't be picked, and a time that's already
 * passed is refused with a note (iOS's picker simply won't go there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReschedulePlanSheet(
    initialTime: Instant,
    isSaving: Boolean,
    failed: Boolean,
    timePassed: Boolean,
    onSubmit: (Instant) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AtxTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val initialLocal = remember(initialTime) { initialTime.atZone(zone) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = RescheduleTime.dayUtcMillis(initialTime, zone),
        selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    RescheduleTime.isSelectableDay(utcTimeMillis, Instant.now(), zone)

                override fun isSelectableYear(year: Int) = year >= Instant.now().atZone(zone).year
            }
        },
    )
    val timeState = rememberTimePickerState(
        initialHour = initialLocal.hour,
        initialMinute = initialLocal.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    // Can't be swiped away while the request is saving.
    val saving by rememberUpdatedState(isSaving)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !saving })

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = colors.appBackground,
        dragHandle = null,
    ) {
        SheetTopBar(stringResource(R.string.plan_reschedule), leading = stringResource(R.string.action_cancel) to onCancel, trailing = null)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.reschedule_title), style = atxText(22.sp, FontWeight.Bold), color = colors.primaryText)
                Text(stringResource(R.string.reschedule_subtitle), style = atxText(14.sp), color = colors.secondaryText)
            }

            Column(
                Modifier.fillMaxWidth().background(colors.cardBackground, RoundedCornerShape(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DatePicker(
                    state = dateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = colors.cardBackground,
                        selectedDayContainerColor = colors.appPrimary,
                        todayDateBorderColor = colors.appPrimary,
                        todayContentColor = colors.appPrimary,
                    ),
                )
                TimeInput(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = colors.appPrimary.copy(alpha = 0.18f),
                        periodSelectorSelectedContainerColor = colors.appPrimary.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (failed || timePassed) {
                Text(
                    stringResource(if (timePassed) R.string.reschedule_time_passed else R.string.plan_error_reschedule),
                    style = atxText(14.sp),
                    color = colors.danger,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.appPrimary)
                    .clickable(enabled = !isSaving && dateState.selectedDateMillis != null, role = Role.Button) {
                        val day = dateState.selectedDateMillis ?: return@clickable
                        onSubmit(RescheduleTime.combine(day, timeState.hour, timeState.minute, zone))
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_msg_refresh), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.reschedule_submit), style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Port of iOS UpcomingPlansSheet: every upcoming confirmed plan after the pinned one, each with
 * "Cancel" (asks first) and, when a new time is pending, the reschedule answer. Live — it closes
 * itself when nothing is left.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingPlansSheet(
    plans: List<Plan>,
    otherUserName: String,
    myID: String,
    dates: MessageDates,
    busyPlanIDs: Set<String>,
    actions: PlanActions,
    onDone: () -> Unit,
) {
    val colors = AtxTheme.colors
    LaunchedEffect(plans.isEmpty()) { if (plans.isEmpty()) onDone() }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
        dragHandle = null,
    ) {
        SheetTopBar(stringResource(R.string.upcoming_plans_title), leading = null, trailing = stringResource(R.string.action_done) to onDone)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            plans.forEach { plan ->
                UpcomingPlanRow(plan, otherUserName, myID, dates, isBusy = plan.id in busyPlanIDs, actions = actions)
            }
        }
    }
}

/** Port of iOS UpcomingPlanRow. */
@Composable
private fun UpcomingPlanRow(plan: Plan, otherUserName: String, myID: String, dates: MessageDates, isBusy: Boolean, actions: PlanActions) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.positiveCardBg)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(PlanColors.positiveGreen))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.activity.name, style = atxText(16.sp, FontWeight.SemiBold), color = colors.primaryText, modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isBusy, role = Role.Button) { actions.confirmCancelFromList(plan.id) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_msg_close), null, tint = PlanColors.declinedRed, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.action_cancel), style = atxText(12.sp, FontWeight.Medium), color = PlanColors.declinedRed)
                }
            }
            plan.standingDate?.let { date ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_msg_clock), null, tint = colors.secondaryText, modifier = Modifier.size(12.dp))
                    Text(dates.proposalDate(date), style = atxText(13.sp), color = colors.secondaryText)
                }
            }
            plan.location?.takeIf { it.isNotEmpty() }?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_msg_place), null, tint = colors.appPrimary, modifier = Modifier.size(12.dp))
                    Text(location, style = atxText(13.sp), color = colors.secondaryText)
                }
            }
            if (plan.status == PlanStatus.COUNTER_PROPOSED) {
                Spacer(Modifier.height(0.dp))
                RescheduleRequest(plan, myID, otherUserName, dates, isBusy, actions)
            }
        }
    }
}

/**
 * iOS's "Cancel this plan?" alert. From the pinned card's ⋯ menu it names the other person;
 * from an upcoming-plans row it also names the activity.
 */
@Composable
fun CancelPlanAlert(plan: Plan, otherUserName: String, fromList: Boolean, onConfirm: () -> Unit, onKeep: () -> Unit) {
    val colors = AtxTheme.colors
    val message = if (fromList) stringResource(R.string.cancel_plan_message_row, plan.activity.name, otherUserName)
    else stringResource(R.string.cancel_plan_message, otherUserName)
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.cancel_plan_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.cancel_plan_confirm), color = colors.danger) }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.cancel_plan_keep), color = colors.appPrimary) }
        },
    )
}
