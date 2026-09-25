package com.georgeappdev.atxfriends.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.plans.DayChoice
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.matches.PhotoPlaceholder
import com.georgeappdev.atxfriends.ui.matches.RemotePhoto
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Port of iOS ProposePlanSheet: the app's one plan composer. [request] picks the mode (and any
 * ghost-card prefill). Cancel always closes without writing. On a successful write the sheet
 * reports [onDone] and closes; on failure it stays open with the error and the input intact.
 *
 * To open it from anywhere (e.g. a Messages thread's "+"), keep a `ComposerRequest?` in state
 * and show `PlanComposerSheet(request, onDismiss = { request = null })` while it's non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanComposerSheet(
    request: ComposerRequest,
    onDismiss: () -> Unit,
    onDone: (ComposerResult) -> Unit = {},
) {
    val viewModel: PlanComposerViewModel = viewModel(key = "composer-${request.id}", factory = PlanComposerViewModel.factory(request))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.result) {
        state.result?.let {
            currentOnDone(it)
            currentOnDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
        dragHandle = null,
    ) {
        Column(Modifier.imePadding()) {
            ComposerTopBar(title = stringResource(titleRes(state.mode)), onCancel = onDismiss)
            ComposerForm(state, viewModel, onClose = onDismiss)
        }
    }
}

@Composable
private fun ComposerTopBar(title: String, onCancel: () -> Unit) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.action_cancel), style = atxText(16.sp), color = colors.appPrimary)
        }
        Text(
            title,
            style = atxText(17.sp, FontWeight.SemiBold),
            color = colors.primaryText,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

@Composable
private fun ComposerForm(state: ComposerUiState, viewModel: PlanComposerViewModel, onClose: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(headerRes(state.mode)),
            style = atxText(22.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (state.isPrefilled) PrefillBanner()

        ActivitySection(state, onChange = viewModel::onActivityChange, onPick = viewModel::onSuggestionPicked)

        if (state.mode is ComposerMode.GroupInvite) {
            InviteeChecklist(state, onToggle = viewModel::onToggleInvitee, onRetry = viewModel::retryInvitees)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposerLabel(R.drawable.ic_tab_upcoming, stringResource(R.string.composer_when_label))
            if (state.mode is ComposerMode.OpenPost) {
                OpenPostWhen(state, onDayChoice = viewModel::onDayChoice, onTime = viewModel::onPostTimePicked)
            } else {
                AnyDateWhen(state, onDate = viewModel::onDatePicked, onTime = viewModel::onTimePicked)
            }
        }

        PlanLocationField(
            where = state.where,
            searchState = state.placeSearch,
            onTextChange = viewModel::onWhereChange,
            onClear = viewModel::onWhereCleared,
            onLeft = viewModel::onWhereLeft,
            onPlacePicked = viewModel::onPlacePicked,
        )

        state.errorMessage?.let {
            Text(
                it,
                style = atxText(14.sp),
                color = colors.danger,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        if (state.waitingForConnection) WaitingForConnection(state.mode, onClose)

        SubmitButton(state, onClick = viewModel::submit)
    }
}

/**
 * Android-only: shown when a send has been waiting ~10 seconds (no connection). The write is
 * queued and goes out once the phone is back online, so closing the sheet loses nothing.
 */
@Composable
private fun WaitingForConnection(mode: ComposerMode, onClose: () -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary.copy(alpha = 0.10f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (mode is ComposerMode.GroupInvite) R.string.composer_offline_invite else R.string.composer_offline_plan),
            style = atxText(14.sp),
            color = colors.primaryText,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_close), style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }
    }
}

/** A section label with its icon (iOS `Label(_, systemImage:)`, 14pt semibold, orange). */
@Composable
internal fun ComposerLabel(icon: Int, text: String) {
    val colors = AtxTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
        Text(text, style = atxText(14.sp, FontWeight.SemiBold), color = colors.appPrimary)
    }
}

@Composable
private fun PrefillBanner() {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.appPrimary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_sparkles), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.composer_prefill_banner), style = atxText(13.sp, FontWeight.Medium), color = colors.appPrimary)
    }
}

@Composable
private fun ActivitySection(state: ComposerUiState, onChange: (String) -> Unit, onPick: (String) -> Unit) {
    val colors = AtxTheme.colors
    val label = stringResource(R.string.composer_activity_label)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComposerLabel(R.drawable.ic_msg_walk, label)
        val textStyle = atxText(16.sp).copy(color = colors.primaryText)
        val placeholder = stringResource(R.string.composer_activity_placeholder)
        BasicTextField(
            value = state.activityName,
            onValueChange = onChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.appPrimary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBackground.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (state.activityName.isEmpty()) Text(placeholder, style = textStyle.copy(color = colors.textSubtle))
                    inner()
                }
            },
        )
        if (state.suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in state.suggestions) {
                    Text(
                        name,
                        style = atxText(14.sp, FontWeight.Medium),
                        color = colors.appPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.appPrimary.copy(alpha = 0.15f))
                            .clickable(role = Role.Button) { onPick(name) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteeChecklist(state: ComposerUiState, onToggle: (String) -> Unit, onRetry: () -> Unit) {
    val colors = AtxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComposerLabel(R.drawable.ic_people, stringResource(R.string.composer_invitees_label))
        when {
            state.isLoadingInvitees -> CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.size(24.dp))
            state.inviteesLoadFailed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.composer_invitees_load_error), style = atxText(14.sp), color = colors.danger)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again), color = colors.appPrimary) }
            }
            state.inviteeOptions.isEmpty() -> Text(stringResource(R.string.composer_no_mutual_matches), style = atxText(14.sp), color = colors.secondaryText)
            else -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardBackground.copy(alpha = 0.85f))) {
                state.inviteeOptions.forEachIndexed { index, option ->
                    val checked = option.userID in state.selectedInviteeIDs
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox) { onToggle(option.userID) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RemotePhoto(
                            url = option.photoURL,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            loading = { PhotoPlaceholder(iconSize = 20.dp, showSpinner = false) },
                        )
                        Text(option.displayName, style = atxText(16.sp, FontWeight.Medium), color = colors.primaryText, modifier = Modifier.weight(1f))
                        Icon(
                            painterResource(if (checked) R.drawable.ic_msg_check_circle else R.drawable.ic_radio_unchecked),
                            contentDescription = null,
                            tint = if (checked) colors.appPrimary else Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (index != state.inviteeOptions.lastIndex) HorizontalDivider(Modifier.padding(start = 14.dp))
                }
            }
        }
    }
}

/** `.openPost`: Today | Tomorrow segments plus a time pill, in one card. */
@Composable
private fun OpenPostWhen(state: ComposerUiState, onDayChoice: (DayChoice) -> Unit, onTime: (LocalTime) -> Unit) {
    val colors = AtxTheme.colors
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SegmentedDayControl(state.dayChoice, todayEnabled = state.todayAvailable, onSelect = onDayChoice, modifier = Modifier.weight(1f))
        TimePill(state.date, state.zone, onClick = { pickingTime = true })
    }
    if (pickingTime) {
        TimePickerDialog(initial = state.date.atZone(state.zone).toLocalTime(), onDismiss = { pickingTime = false }) {
            pickingTime = false
            onTime(it)
        }
    }
}

@Composable
private fun SegmentedDayControl(selected: DayChoice, todayEnabled: Boolean, onSelect: (DayChoice) -> Unit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(modifier.clip(RoundedCornerShape(9.dp)).background(Color.Gray.copy(alpha = 0.15f)).padding(2.dp)) {
        for (choice in DayChoice.entries) {
            val isSelected = choice == selected
            val enabled = choice != DayChoice.TODAY || todayEnabled
            Text(
                stringResource(if (choice == DayChoice.TODAY) R.string.composer_today else R.string.composer_tomorrow),
                style = atxText(13.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (!enabled) colors.textSubtle else colors.primaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) colors.cardBackground else Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(choice) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/**
 * `.proposal` / `.groupInvite`: iOS's graphical picker — a month calendar (today onward) with
 * the time below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnyDateWhen(state: ComposerUiState, onDate: (LocalDate) -> Unit, onTime: (LocalTime) -> Unit) {
    val colors = AtxTheme.colors
    val zone = state.zone
    val selectedDay = state.date.atZone(zone).toLocalDate()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(zone) { FromToday(zone) },
    )
    val currentOnDate by rememberUpdatedState(onDate)
    LaunchedEffect(datePickerState) {
        snapshotFlow { datePickerState.selectedDateMillis }.collect { millis ->
            millis?.let { currentOnDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        }
    }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardBackground.copy(alpha = 0.85f)),
    ) {
        DatePicker(
            state = datePickerState,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                selectedDayContainerColor = colors.appPrimary,
                todayDateBorderColor = colors.appPrimary,
                todayContentColor = colors.appPrimary,
                selectedYearContainerColor = colors.appPrimary,
            ),
        )
        HorizontalDivider(Modifier.padding(horizontal = 14.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.composer_time), style = atxText(16.sp), color = colors.primaryText, modifier = Modifier.weight(1f))
            TimePill(state.date, zone, onClick = { pickingTime = true })
        }
    }
    if (pickingTime) {
        TimePickerDialog(initial = state.date.atZone(zone).toLocalTime(), onDismiss = { pickingTime = false }) {
            pickingTime = false
            onTime(it)
        }
    }
}

/** Days before today (in the device's zone) are greyed out, like iOS `in: Date()...`. */
@OptIn(ExperimentalMaterial3Api::class)
private class FromToday(private val zone: ZoneId) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(LocalDate.now(zone))

    override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now(zone).year
}

/** The grey time pill (iOS compact DatePicker). */
@Composable
private fun TimePill(date: Instant, zone: ZoneId, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    val time = rememberShortTime()(date.atZone(zone))
    val a11y = stringResource(R.string.composer_choose_time)
    Text(
        time,
        style = atxText(16.sp, FontWeight.Medium),
        color = colors.appPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.15f))
            .clickable(role = Role.Button, onClickLabel = a11y, onClick = onClick)
            .semantics { stateDescription = time }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val colors = AtxTheme.colors
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.appBackground,
        text = {
            TimePicker(
                state = pickerState,
                colors = TimePickerDefaults.colors(
                    selectorColor = colors.appPrimary,
                    timeSelectorSelectedContainerColor = colors.appPrimary.copy(alpha = 0.2f),
                    periodSelectorSelectedContainerColor = colors.appPrimary.copy(alpha = 0.2f),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text(stringResource(R.string.action_ok), color = colors.appPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.appPrimary) }
        },
    )
}

@Composable
private fun SubmitButton(state: ComposerUiState, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    val label = stringResource(submitRes(state.mode))
    val sending = stringResource(R.string.composer_submitting)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (state.canSubmit || state.isSubmitting) colors.appPrimary else Color.Gray.copy(alpha = 0.4f))
            .clickable(enabled = state.canSubmit, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (state.isSubmitting) stateDescription = sending }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(
                painterResource(if (state.mode is ComposerMode.Proposal) R.drawable.ic_calendar_add else R.drawable.ic_send),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = atxText(17.sp, FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The device's short time format ("7:00 PM", or "19:00" with 24-hour time on). */
@Composable
internal fun rememberShortTime(): (ZonedDateTime) -> String {
    val context = LocalContext.current
    return remember(context) {
        val locale = context.resources.configuration.locales[0]
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "Hm" else "hma"
        val formatter = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        formatter::format
    }
}

private fun titleRes(mode: ComposerMode) = when (mode) {
    is ComposerMode.Proposal -> R.string.composer_title_proposal
    ComposerMode.OpenPost -> R.string.composer_title_open_post
    ComposerMode.GroupInvite -> R.string.composer_title_group_invite
}

private fun headerRes(mode: ComposerMode) = when (mode) {
    is ComposerMode.Proposal -> R.string.composer_header_proposal
    ComposerMode.OpenPost -> R.string.composer_header_open_post
    ComposerMode.GroupInvite -> R.string.composer_header_group_invite
}

private fun submitRes(mode: ComposerMode) = when (mode) {
    is ComposerMode.Proposal -> R.string.composer_submit_proposal
    ComposerMode.OpenPost -> R.string.composer_submit_open_post
    ComposerMode.GroupInvite -> R.string.composer_submit_group_invite
}
