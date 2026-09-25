package com.georgeappdev.atxfriends.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.domain.messages.MessageDates
import com.georgeappdev.atxfriends.domain.messages.canRespondToReschedule
import com.georgeappdev.atxfriends.domain.messages.collapsedSubtitle
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

private val CardShape = RoundedCornerShape(22.dp)

/**
 * Port of iOS PinnedPlanCard: the soonest confirmed plan, pinned above the messages, as an
 * expanded "ticket" or a collapsed strip, with the ⋯ menu (Reschedule / Cancel plan), "+N more",
 * the pending-reschedule answer, directions, and Add to Calendar.
 */
@Composable
fun PinnedPlanCard(
    plan: Plan,
    overflowCount: Int,
    otherUserName: String,
    otherUserPhotoURL: String?,
    myPhotoURL: String?,
    myID: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    dates: MessageDates,
    isBusy: Boolean,
    addedToCalendar: Boolean,
    actions: PlanActions,
) {
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        if (expanded) {
            ExpandedTicket(plan, overflowCount, otherUserName, otherUserPhotoURL, myPhotoURL, myID, dates, isBusy, addedToCalendar, actions)
        } else {
            CollapsedStrip(plan, onExpand, dates, onDirections = { actions.directions(plan) })
        }
    }
}

private fun Modifier.cardBackground(cardColor: Color) = this
    .shadow(8.dp, CardShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
    .background(cardColor, CardShape)
    .border(1.dp, Color.Gray.copy(alpha = 0.2f), CardShape)

private val Plan.hasLocation: Boolean
    get() = (locationLatitude != null && locationLongitude != null) || !location.isNullOrEmpty()

@Composable
private fun ExpandedTicket(
    plan: Plan,
    overflowCount: Int,
    otherUserName: String,
    otherUserPhotoURL: String?,
    myPhotoURL: String?,
    myID: String,
    dates: MessageDates,
    isBusy: Boolean,
    addedToCalendar: Boolean,
    actions: PlanActions,
) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().cardBackground(colors.cardBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopRow(
            overflowCount = overflowCount,
            // One reschedule request at a time — hidden for both people while one is pending.
            canReschedule = plan.status == PlanStatus.CONFIRMED,
            enabled = !isBusy,
            onShowAll = actions.showAll,
            onReschedule = { actions.openReschedule(plan.id) },
            onCancel = { actions.confirmCancel(plan.id) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dates.pinnedHeadline(plan), style = atxText(28.sp, FontWeight.Bold), color = colors.primaryText)
            Text(dates.pinnedSubline(plan), style = atxText(15.sp), color = colors.secondaryText)
        }

        if (plan.status == PlanStatus.COUNTER_PROPOSED) {
            RescheduleRequest(plan, myID, otherUserName, dates, isBusy, actions)
        }

        PeopleRow(otherUserName, otherUserPhotoURL, myPhotoURL)

        LocationSection(plan, onDirections = { actions.directions(plan) })

        ButtonsRow(plan, addedToCalendar, onDirections = { actions.directions(plan) }, onAddToCalendar = { actions.addToCalendar(plan) })
    }
}

@Composable
private fun TopRow(
    overflowCount: Int,
    canReschedule: Boolean,
    enabled: Boolean,
    onShowAll: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AtxTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .background(PlanColors.positiveGreen.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(painterResource(R.drawable.ic_msg_check_circle), null, tint = PlanColors.positiveGreen, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.plan_confirmed), style = atxText(13.sp, FontWeight.SemiBold), color = PlanColors.positiveGreen)
        }

        if (overflowCount > 0) {
            Text(
                stringResource(R.string.plan_more_count, overflowCount),
                style = atxText(12.sp, FontWeight.SemiBold),
                color = PlanColors.positiveGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlanColors.positiveGreen.copy(alpha = 0.12f))
                    .clickable(role = Role.Button, onClick = onShowAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        var menuOpen by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = { menuOpen = true }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_msg_more),
                    stringResource(R.string.plan_more_options),
                    tint = colors.primaryText,
                    modifier = Modifier.size(22.dp),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = colors.cardBackground,
            ) {
                if (canReschedule) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plan_reschedule), style = atxText(16.sp), color = colors.primaryText) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_msg_refresh), null, tint = colors.primaryText, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuOpen = false
                            onReschedule()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_cancel_plan), style = atxText(16.sp), color = colors.danger) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_msg_close), null, tint = colors.danger, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        menuOpen = false
                        onCancel()
                    },
                )
            }
        }
    }
}

/**
 * Port of iOS RescheduleRequestView: who suggested which new time, and Accept/Decline for the
 * person who can answer or "Waiting for …" for the requester. Also used by the upcoming-plans rows.
 */
@Composable
fun RescheduleRequest(plan: Plan, myID: String, otherUserName: String, dates: MessageDates, isBusy: Boolean, actions: PlanActions) {
    val colors = AtxTheme.colors
    val canRespond = plan.canRespondToReschedule(myID)
    Column(
        Modifier
            .fillMaxWidth()
            .background(PlanColors.pendingGoldTint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_msg_refresh), null, tint = PlanColors.pendingGold, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    dates.rescheduleSuggestion(plan, myID, otherUserName),
                    style = atxText(14.sp, FontWeight.SemiBold),
                    color = colors.primaryText,
                )
                if (!canRespond) {
                    Text(stringResource(R.string.plan_waiting_for, otherUserName), style = atxText(13.sp), color = colors.secondaryText)
                }
            }
        }
        if (canRespond) {
            AcceptDeclineRow(
                isBusy = isBusy,
                onAccept = { actions.acceptReschedule(plan.id) },
                onDecline = { actions.declineReschedule(plan.id) },
                acceptHint = stringResource(R.string.plan_reschedule_accept_hint),
                declineHint = stringResource(R.string.plan_reschedule_decline_hint),
            )
        }
    }
}

@Composable
private fun PeopleRow(otherUserName: String, otherUserPhotoURL: String?, myPhotoURL: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 50.dp, height = 30.dp)) {
            PersonAvatar(otherUserPhotoURL, otherUserName.take(1), Modifier.offset(x = 20.dp))
            PersonAvatar(myPhotoURL, stringResource(R.string.plan_you_initial))
        }
        Text(
            stringResource(R.string.plan_you_and, otherUserName),
            style = atxText(14.sp, FontWeight.Medium),
            color = AtxTheme.colors.primaryText,
        )
    }
}

@Composable
private fun PersonAvatar(url: String?, fallbackInitial: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    val shape = Modifier.size(30.dp).clip(CircleShape)
    Box(modifier.size(30.dp).border(2.dp, colors.cardBackground, CircleShape)) {
        val fallback: @Composable () -> Unit = { InitialsCircle(fallbackInitial, 12.sp) }
        if (url == null) {
            Box(shape) { fallback() }
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = shape,
                loading = { fallback() },
                error = { fallback() },
            )
        }
    }
}

/**
 * iOS shows a MapKit snapshot here when the plan has coordinates. Android has no map yet, so it
 * shows the same tinted box and place label, with a pin in place of the map. Tapping it opens
 * directions, as on iOS.
 */
@Composable
private fun LocationSection(plan: Plan, onDirections: () -> Unit) {
    val colors = AtxTheme.colors
    val directions = stringResource(R.string.plan_get_directions)
    if (plan.locationLatitude != null && plan.locationLongitude != null) {
        val secondary = plan.location?.takeIf { it.isNotEmpty() && it != plan.locationName }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.appPrimary.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClickLabel = directions, onClick = onDirections),
        ) {
            Icon(
                painterResource(R.drawable.ic_msg_place),
                null,
                tint = colors.appPrimary,
                modifier = Modifier.size(28.dp).align(Alignment.Center),
            )
            plan.locationName?.takeIf { it.isNotEmpty() }?.let { name ->
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(name, style = atxText(13.sp, FontWeight.Bold), color = colors.appNavy)
                    secondary?.let {
                        Text(
                            it,
                            style = atxText(11.sp),
                            color = colors.appNavy.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else if (!plan.location.isNullOrEmpty()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = directions, onClick = onDirections),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_msg_place), null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
            Text(plan.location, style = atxText(14.sp), color = colors.primaryText)
        }
    }
}

/**
 * "Get directions" plus the calendar icon when the plan has a place, otherwise a full-width
 * "Add to Calendar". Once added (remembered per plan on this device) the icon becomes a check
 * and the label "Added to Calendar".
 */
@Composable
private fun ButtonsRow(plan: Plan, addedToCalendar: Boolean, onDirections: () -> Unit, onAddToCalendar: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val calendarIcon = if (addedToCalendar) R.drawable.ic_msg_check_circle else R.drawable.ic_msg_calendar_plus
    val calendarLabel = stringResource(if (addedToCalendar) R.string.plan_added_to_calendar else R.string.plan_add_to_calendar)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (plan.hasLocation) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .clip(shape)
                    .background(colors.appPrimary)
                    .clickable(role = Role.Button, onClick = onDirections),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.plan_get_directions), style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
                Icon(painterResource(R.drawable.ic_msg_arrow_forward), null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier
                    .size(50.dp)
                    .clip(shape)
                    .background(colors.appPrimary.copy(alpha = 0.12f))
                    .clickable(role = Role.Button, onClick = onAddToCalendar)
                    .semantics { contentDescription = calendarLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(calendarIcon), null, tint = colors.appPrimary, modifier = Modifier.size(22.dp))
            }
        } else {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .clip(shape)
                    .background(colors.appPrimary)
                    .clickable(role = Role.Button, onClick = onAddToCalendar),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(painterResource(calendarIcon), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(calendarLabel, style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}

@Composable
private fun CollapsedStrip(plan: Plan, onExpand: () -> Unit, dates: MessageDates, onDirections: () -> Unit) {
    val colors = AtxTheme.colors
    val expandLabel = stringResource(R.string.plan_show_details)
    val directionsLabel = stringResource(R.string.plan_get_directions)
    Row(
        Modifier.fillMaxWidth().cardBackground(colors.cardBackground).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(PlanColors.positiveGreen, CircleShape))

        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(dates.pinnedHeadline(plan), style = atxText(15.sp, FontWeight.Bold), color = colors.primaryText)
            Text(
                plan.collapsedSubtitle(),
                style = atxText(12.sp),
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (plan.hasLocation) {
            CircleIcon(
                icon = R.drawable.ic_msg_place,
                size = 44.dp,
                iconSize = 18.dp,
                background = colors.appPrimary,
                tint = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plan_get_directions), onClick = onDirections)
                    .semantics { contentDescription = directionsLabel },
            )
        }

        Box(
            Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = expandLabel, onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_msg_expand_more), expandLabel, tint = colors.primaryText, modifier = Modifier.size(22.dp))
        }
    }
}
