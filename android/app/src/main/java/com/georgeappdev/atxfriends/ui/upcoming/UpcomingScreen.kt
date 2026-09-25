package com.georgeappdev.atxfriends.ui.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.openslots.OpenSlot
import com.georgeappdev.atxfriends.domain.upcoming.DayDot
import com.georgeappdev.atxfriends.domain.upcoming.MyPlanStatus
import com.georgeappdev.atxfriends.ui.components.GhostCardStyle
import com.georgeappdev.atxfriends.ui.components.GhostSlotCard
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.plans.ComposerMode
import com.georgeappdev.atxfriends.ui.plans.ComposerPrefill
import com.georgeappdev.atxfriends.ui.plans.ComposerRequest
import com.georgeappdev.atxfriends.ui.plans.ComposerResult
import com.georgeappdev.atxfriends.ui.plans.PlanComposerSheet
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Port of iOS UpcomingView: plans later today under "Today", a 7-day strip (tomorrow through +7)
 * with dots, then each day's group plans — or, only on a day with none, one dashed "…is open"
 * ghost slot — and plans past the strip under "Later". "+" and a ghost
 * slot's "Invite" open the composer (.groupInvite); a card opens GroupPlanDetail. Live while
 * on screen.
 */
@Composable
fun UpcomingScreen(viewModel: UpcomingViewModel = viewModel(factory = UpcomingViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors

    val vm by rememberUpdatedState(viewModel)
    DisposableEffect(Unit) {
        vm.onAppear()
        onDispose { vm.onDisappear() }
    }

    var composer by remember { mutableStateOf<ComposerRequest?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    val listState = rememberLazyListState()
    val sent = state.justSent
    // Keep the "Invite sent" banner and the highlight up for a few seconds, then clear them.
    LaunchedEffect(sent?.planID) {
        if (sent != null) {
            delay(SENT_CONFIRMATION_MS)
            viewModel.clearJustSent()
        }
    }
    // Scroll to the new invite once the listener has placed it in a section.
    val itemKeys = upcomingItemKeys(state)
    LaunchedEffect(sent?.sectionKey) {
        val index = sent?.sectionKey?.let(itemKeys::indexOf) ?: -1
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(Modifier.fillMaxSize().background(colors.appBackground)) {
        UpcomingTopBar(onNewPlan = { composer = ComposerRequest(ComposerMode.GroupInvite) })
        sent?.let { InviteSentBanner(it, state.zone, locale) }

        when {
            state.isLoading -> CenteredLoading()
            state.loadFailed -> LoadFailed(onRetry = viewModel::retry)
            !state.hasAnyContent -> EmptyUpcoming()
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val highlighted = sent?.planID
                if (state.todayPlans.isNotEmpty()) item(key = SECTION_TODAY) {
                    PlanListSection(stringResource(R.string.upcoming_today), state.todayPlans, highlighted, state.zone, locale, viewModel::openDetail)
                }
                item(key = "strip") { WeekStrip(state.days, locale) }
                for (day in state.days) {
                    when {
                        day.plans.isNotEmpty() -> item(key = daySectionKey(day.date)) {
                            DaySection(day, highlighted, state.zone, locale, onOpen = viewModel::openDetail)
                        }
                        day.ghost != null -> item(key = "ghost-${day.date}") {
                            UpcomingGhost(day.date, day.ghost, locale) {
                                composer = ComposerRequest(ComposerMode.GroupInvite, ComposerPrefill(day.ghost.activityName, day.ghost.start))
                            }
                        }
                    }
                }
                if (state.laterPlans.isNotEmpty()) item(key = SECTION_LATER) {
                    PlanListSection(stringResource(R.string.upcoming_later), state.laterPlans, highlighted, state.zone, locale, viewModel::openDetail)
                }
            }
        }
    }

    state.detail?.let { detail ->
        GroupPlanDetailSheet(
            detail = detail,
            zone = state.zone,
            isResponding = state.isResponding,
            isCancelling = state.isCancelling,
            onRespond = viewModel::respond,
            onCancelPlan = viewModel::cancelPlan,
            onDismiss = viewModel::closeDetail,
        )
    }

    composer?.let { request ->
        PlanComposerSheet(
            request,
            onDismiss = { composer = null },
            onDone = { result -> if (result is ComposerResult.Invited) viewModel.onInviteSent(result.planID) },
        )
    }

    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.upcoming_error_title)) },
            text = {
                Text(stringResource(if (error == UpcomingError.RESPOND) R.string.group_detail_respond_error else R.string.group_detail_cancel_error))
            },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

private const val SENT_CONFIRMATION_MS = 4_000L

/** The LazyColumn's item keys, in order, matching the items [UpcomingScreen] emits. */
private fun upcomingItemKeys(state: UpcomingUiState): List<String> = buildList {
    if (state.todayPlans.isNotEmpty()) add(SECTION_TODAY)
    add("strip")
    for (day in state.days) {
        when {
            day.plans.isNotEmpty() -> add(daySectionKey(day.date))
            day.ghost != null -> add("ghost-${day.date}")
        }
    }
    if (state.laterPlans.isNotEmpty()) add(SECTION_LATER)
}

/**
 * Android-only: confirms the composer's invite went out, with its day and time, so the sheet
 * closing is never the only sign. Announced by TalkBack.
 */
@Composable
private fun InviteSentBanner(sent: SentInvite, zone: ZoneId, locale: Locale) {
    val colors = AtxTheme.colors
    val text = sent.plan?.let { plan ->
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        stringResource(R.string.upcoming_invite_sent_for, formatter.format(plan.date.atZone(zone)))
    } ?: stringResource(R.string.upcoming_invite_sent)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
        Text(text, style = atxText(14.sp, FontWeight.SemiBold), color = colors.primaryText)
    }
}

/** A titled list of plan cards: "Today" above the strip, "Later" below it. */
@Composable
private fun PlanListSection(title: String, plans: List<GroupPlanUi>, highlighted: String?, zone: ZoneId, locale: Locale, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = atxText(15.sp, FontWeight.Bold), color = AtxTheme.colors.primaryText, modifier = Modifier.semantics { heading() })
        for (plan in plans) GroupPlanCard(plan, zone, locale, isHighlighted = plan.id == highlighted, onClick = { onOpen(plan.id) })
    }
}

/** iOS large navigation title with the + toolbar button. */
@Composable
private fun UpcomingTopBar(onNewPlan: () -> Unit) {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.upcoming_new_plan),
                tint = colors.appPrimary,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onNewPlan),
            )
        }
        Text(
            stringResource(R.string.upcoming_title),
            style = atxText(34.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(top = 2.dp).semantics { heading() },
        )
    }
}

@Composable
private fun CenteredLoading() {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.upcoming_loading), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
        }
    }
}

/** Android-only: iOS keeps its spinner forever if the listener fails. */
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.danger, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.upcoming_load_error), style = atxText(15.sp), color = colors.textBody, textAlign = TextAlign.Center)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again), color = colors.appPrimary) }
        }
    }
}

@Composable
private fun EmptyUpcoming() {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_tab_upcoming), contentDescription = null, tint = colors.appPrimary.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.upcoming_empty_title), style = atxText(22.sp, FontWeight.SemiBold), color = colors.primaryText)
                Text(stringResource(R.string.upcoming_empty_body), style = atxText(15.sp), color = colors.textTertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WeekStrip(days: List<UpcomingDayUi>, locale: Locale) {
    val colors = AtxTheme.colors
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val full = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val planA11y = stringResource(R.string.upcoming_day_a11y_plan)
    val slotA11y = stringResource(R.string.upcoming_day_a11y_slot)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        for (day in days) {
            val label = full.format(day.date)
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = when (day.dot) {
                        DayDot.PLAN -> planA11y.format(label)
                        DayDot.SLOT -> slotA11y.format(label)
                        DayDot.NONE -> label
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(weekday.format(day.date), style = atxText(12.sp, FontWeight.SemiBold), color = colors.textTertiary)
                Text(day.date.dayOfMonth.toString(), style = atxText(15.sp, FontWeight.Bold), color = colors.primaryText)
                val dot = Modifier.size(8.dp).clip(CircleShape)
                when (day.dot) {
                    DayDot.PLAN -> Box(dot.background(colors.appPrimary))
                    DayDot.SLOT -> Box(dot.border(1.5.dp, colors.appPrimary, CircleShape))
                    DayDot.NONE -> Box(dot)
                }
            }
        }
    }
}

@Composable
private fun DaySection(day: UpcomingDayUi, highlighted: String?, zone: ZoneId, locale: Locale, onOpen: (String) -> Unit) {
    val colors = AtxTheme.colors
    val header = remember(locale) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(header.format(day.date), style = atxText(15.sp, FontWeight.Bold), color = colors.primaryText, modifier = Modifier.semantics { heading() })
        for (plan in day.plans) GroupPlanCard(plan, zone, locale, isHighlighted = plan.id == highlighted, onClick = { onOpen(plan.id) })
    }
}

/** A dashed ghost slot: "{Weekday} {time slot} is open" + "Invite". */
@Composable
private fun UpcomingGhost(date: LocalDate, slot: OpenSlot, locale: Locale, onInvite: () -> Unit) {
    val dayLabel = remember(locale, date) { DateTimeFormatter.ofPattern("EEEE", locale).format(date) }
    val slotLabel = slot.timeSlot.raw.orEmpty().lowercase()
    GhostSlotCard(
        titleLine = stringResource(R.string.upcoming_ghost_title, dayLabel, slotLabel),
        activityLine = stringResource(R.string.today_ghost_activity, slot.activityName),
        style = GhostCardStyle.DASHED,
        buttonIcon = R.drawable.ic_send,
        buttonLabel = stringResource(R.string.upcoming_invite),
        accessibilityLabel = stringResource(R.string.upcoming_ghost_a11y, slot.activityName, dayLabel, slotLabel),
        onTap = onInvite,
    )
}

/** Port of iOS GroupPlanCard. [isHighlighted] outlines an invite that was just sent. */
@Composable
private fun GroupPlanCard(plan: GroupPlanUi, zone: ZoneId, locale: Locale, isHighlighted: Boolean, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val pill = remember(locale, zone, plan.date) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(plan.date.atZone(zone))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(shape)
            .background(colors.cardBackground.copy(alpha = 0.85f))
            .then(if (isHighlighted) Modifier.border(2.dp, colors.appPrimary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(plan.activityName, style = atxText(18.sp, FontWeight.Bold), color = colors.primaryText, modifier = Modifier.weight(1f))
            Text(
                pill,
                style = atxText(13.sp, FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.appPrimary)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        IconRow(R.drawable.ic_person_circle, 13.dp, stringResource(R.string.upcoming_hosted_by, plan.hostName ?: stringResource(R.string.upcoming_unknown_name)))
        plan.location?.let { IconRow(R.drawable.ic_location, 12.dp, it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.upcoming_going_count, plan.goingCount),
                style = atxText(13.sp, FontWeight.Medium),
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(statusRes(plan.myStatus)), style = atxText(13.sp, FontWeight.SemiBold), color = colors.appPrimary)
        }
    }
}

@Composable
private fun IconRow(icon: Int, iconSize: androidx.compose.ui.unit.Dp, text: String) {
    val colors = AtxTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(iconSize))
        Text(text, style = atxText(13.sp, FontWeight.Medium), color = colors.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun statusRes(status: MyPlanStatus) = when (status) {
    MyPlanStatus.HOSTING -> R.string.upcoming_status_hosting
    MyPlanStatus.GOING -> R.string.upcoming_status_going
    MyPlanStatus.CANT_MAKE -> R.string.upcoming_status_cant_make
    MyPlanStatus.INVITED -> R.string.upcoming_status_invited
}
