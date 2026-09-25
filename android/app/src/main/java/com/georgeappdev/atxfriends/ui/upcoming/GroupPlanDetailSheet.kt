package com.georgeappdev.atxfriends.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.ui.components.CalendarEventFields
import com.georgeappdev.atxfriends.ui.components.PlanExternalActions
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** iOS `appPositiveGreen` / `appDeclinedRed` (fixed, not light/dark adaptive). */
private val PositiveGreen = Color(0xFF4D9459)
private val DeclinedRed = Color(0xFFB85447)

/**
 * Port of iOS GroupPlanDetailView: the plan, who's invited and their answers, Going / Can't
 * make it for invitees (only their own answer is written), Cancel Plan for the host (with the
 * iOS confirm alert), directions from the location row, and Add to Calendar. Always shows the
 * live plan, so another person's answer or a cancel elsewhere appears while it's open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupPlanDetailSheet(
    detail: GroupPlanDetailUi,
    zone: ZoneId,
    isResponding: Boolean,
    isCancelling: Boolean,
    onRespond: (GroupPlanResponse) -> Unit,
    onCancelPlan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AtxTheme.colors
    val context = LocalContext.current
    var confirmingCancel by rememberSaveable { mutableStateOf(false) }

    val unknown = stringResource(R.string.upcoming_unknown_name)
    val hostName = detail.hostName ?: unknown
    val notes = stringResource(R.string.group_detail_calendar_notes, hostName, detail.invitees.joinToString(", ") { it.name ?: unknown })
    val addToCalendar = {
        PlanExternalActions.addToCalendar(context, CalendarEventFields(detail.activityName, detail.date, detail.location, notes))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.appBackground,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(stringResource(R.string.group_detail_done), style = atxText(16.sp), color = colors.appPrimary)
            }
            Text(
                stringResource(R.string.group_detail_title),
                style = atxText(17.sp, FontWeight.SemiBold),
                color = colors.primaryText,
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
            )
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HeaderCard(detail, hostName, zone, onDirections = {
                PlanExternalActions.openDirections(context, detail.location, detail.latitude, detail.longitude)
            })
            InviteesCard(detail, unknown)

            if (detail.isInvitee) {
                ResponseButtons(detail.myResponse, isResponding, onRespond, Modifier.padding(horizontal = 20.dp))
            }

            if (detail.isHost) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalendarButton(addToCalendar, Modifier.weight(1f))
                    CancelPlanButton(isCancelling, onClick = { confirmingCancel = true }, modifier = Modifier.weight(1f))
                }
            } else {
                CalendarButton(addToCalendar, Modifier.padding(horizontal = 20.dp))
            }
        }
    }

    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text(stringResource(R.string.group_detail_cancel_title)) },
            text = { Text(stringResource(R.string.group_detail_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCancel = false
                    onCancelPlan()
                }) { Text(stringResource(R.string.group_detail_cancel_plan), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancel = false }) { Text(stringResource(R.string.group_detail_keep_plan), color = colors.appPrimary) }
            },
        )
    }
}

@Composable
private fun HeaderCard(detail: GroupPlanDetailUi, hostName: String, zone: ZoneId, onDirections: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val locale = LocalConfiguration.current.locales[0]
    val whenText = remember(locale, zone, detail.date) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale).format(detail.date.atZone(zone))
    }
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(colors.cardBackground.copy(alpha = 0.9f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (detail.isCancelled) Color.Gray else colors.appPrimary)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painterResource(if (detail.isCancelled) R.drawable.ic_close_circle else R.drawable.ic_tab_upcoming),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(if (detail.isCancelled) R.string.group_detail_cancelled else R.string.group_detail_active),
                    style = atxText(14.sp, FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }
        InfoRow(R.drawable.ic_msg_walk, stringResource(R.string.group_detail_activity), detail.activityName)
        InfoRow(R.drawable.ic_msg_clock, stringResource(R.string.group_detail_when), whenText)
        detail.location?.let { location ->
            InfoRow(
                R.drawable.ic_location,
                stringResource(R.string.group_detail_location),
                location,
                valueSize = 16,
                trailingChevron = true,
                modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.group_detail_directions), onClick = onDirections),
            )
        }
        InfoRow(R.drawable.ic_person_circle, stringResource(R.string.group_detail_host), hostName)
    }
}

@Composable
private fun InfoRow(icon: Int, label: String, value: String, modifier: Modifier = Modifier, valueSize: Int = 18, trailingChevron: Boolean = false) {
    val colors = AtxTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = atxText(12.sp, FontWeight.Medium), color = colors.secondaryText)
            Text(value, style = atxText(valueSize.sp, if (valueSize == 18) FontWeight.SemiBold else FontWeight.Medium), color = colors.primaryText)
        }
        if (trailingChevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun InviteesCard(detail: GroupPlanDetailUi, unknown: String) {
    val colors = AtxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.group_detail_whos_invited),
            style = atxText(20.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
        )
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardBackground.copy(alpha = 0.85f)),
        ) {
            detail.invitees.forEachIndexed { index, invitee ->
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(invitee.name ?: unknown, style = atxText(16.sp, FontWeight.Medium), color = colors.primaryText, modifier = Modifier.weight(1f))
                    val (label, color) = when (invitee.response) {
                        GroupPlanResponse.GOING -> R.string.upcoming_status_going to PositiveGreen
                        GroupPlanResponse.CANT_MAKE -> R.string.upcoming_status_cant_make to DeclinedRed
                        else -> R.string.upcoming_status_invited to colors.textMuted
                    }
                    Text(stringResource(label), style = atxText(14.sp, FontWeight.SemiBold), color = color)
                }
                if (index != detail.invitees.lastIndex) HorizontalDivider(Modifier.padding(start = 14.dp))
            }
        }
    }
}

/** "Can't make it" (red outline, filled when chosen) and "Going" (orange, navy when chosen). */
@Composable
private fun ResponseButtons(myResponse: GroupPlanResponse?, isResponding: Boolean, onRespond: (GroupPlanResponse) -> Unit, modifier: Modifier) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val cantMake = myResponse == GroupPlanResponse.CANT_MAKE
    val going = myResponse == GroupPlanResponse.GOING
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionButton(
            label = stringResource(R.string.group_detail_cant_make),
            textColor = if (cantMake) Color.White else DeclinedRed,
            background = if (cantMake) DeclinedRed else colors.cardBackground.copy(alpha = 0.8f),
            border = DeclinedRed,
            enabled = !isResponding,
            isSelected = cantMake,
            onClick = { onRespond(GroupPlanResponse.CANT_MAKE) },
            modifier = Modifier.weight(1f),
            shape = shape,
        )
        ActionButton(
            label = stringResource(R.string.group_detail_going),
            textColor = Color.White,
            background = if (going) colors.appNavy else colors.appPrimary,
            border = null,
            enabled = !isResponding,
            isSelected = going,
            onClick = { onRespond(GroupPlanResponse.GOING) },
            modifier = Modifier.weight(1f),
            shape = shape,
        )
    }
}

@Composable
private fun CalendarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.appPrimary)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_calendar_add), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.group_detail_add_to_calendar), style = atxText(15.sp, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun CancelPlanButton(isCancelling: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    ActionButton(
        label = stringResource(R.string.group_detail_cancel_plan),
        textColor = colors.danger,
        background = colors.cardBackground.copy(alpha = 0.8f),
        border = colors.danger,
        enabled = !isCancelling,
        showSpinner = isCancelling,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        textSize = 16,
    )
}

@Composable
private fun ActionButton(
    label: String,
    textColor: Color,
    background: Color,
    border: Color?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    shape: RoundedCornerShape,
    isSelected: Boolean = false,
    showSpinner: Boolean = false,
    textSize: Int = 15,
    borderWidth: Dp = 2.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = textColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, style = atxText(textSize.sp, FontWeight.SemiBold), color = textColor)
        }
    }
}
