package com.georgeappdev.atxfriends.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.today.OpenSlotsHeader
import com.georgeappdev.atxfriends.domain.today.TodayFeed
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Port of iOS TodayView, read-only this round: "I'm in", "Post it", the next-usual-slot link,
 * "Or start from scratch", and the + button are shown in the iOS style but do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val shortTime = rememberShortTimeFormatter()

    // iOS .task / .onDisappear: load + listen while on screen, stop listening when leaving.
    val vm by rememberUpdatedState(viewModel)
    DisposableEffect(Unit) {
        vm.onAppear()
        onDispose { vm.onDisappear() }
    }

    Box(Modifier.fillMaxSize().background(colors.appBackground)) {
        Column(Modifier.fillMaxSize()) {
            TodayTopBar()

            // Filter chips only when there's something to filter.
            if (state.availableActivities.isNotEmpty()) {
                ActivityFilterBar(
                    activities = state.availableActivities,
                    selected = state.activityFilter,
                    onAll = viewModel::selectAll,
                    onSelect = viewModel::selectActivity,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (state.isLoading) {
                TodayLoading()
            } else {
                PullToRefreshBox(isRefreshing = false, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.plans.isEmpty()) {
                            item(key = "empty") { EmptyLine(state.activityFilter) }
                        } else {
                            items(state.plans, key = { it.id }) { plan ->
                                TodayPlanCard(
                                    plan = plan,
                                    timeBadge = TodayFeed.timeBadgeLabel(plan.scheduledTime, state.now, state.zone, shortTime),
                                )
                            }
                        }
                        if (state.openSlots.isNotEmpty()) {
                            item(key = "openSlots") { OpenSlotsSection(state) }
                        }
                    }
                }
            }
        }

        // Poster banner: slides down when someone claims your plan.
        AnimatedVisibility(
            visible = state.isClaimedBannerVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            state.claimedBannerActivity?.let { ClaimedBanner(stringResource(R.string.today_claimed_banner, it)) }
        }
    }

    state.loadError?.let { detail ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.today_error_title)) },
            text = { Text(stringResource(R.string.today_load_error, detail)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** The device's short time format ("7:00 PM", or "19:00" with 24-hour time on), like iOS `.shortened`. */
@Composable
private fun rememberShortTimeFormatter(): (ZonedDateTime) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val locale = configuration.locales[0]
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "Hm" else "hma"
        val formatter = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        formatter::format
    }
}

/** iOS large navigation title with the + toolbar button (disabled this round). */
@Composable
private fun TodayTopBar() {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.today_new_plan),
                tint = colors.appPrimary,
                modifier = Modifier
                    .size(28.dp)
                    .semantics { role = Role.Button; disabled() },
            )
        }
        Text(
            stringResource(R.string.today_title),
            style = atxText(34.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun TodayLoading() {
    val colors = AtxTheme.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.today_loading), style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText)
        }
    }
}

@Composable
private fun ActivityFilterBar(
    activities: List<String>,
    selected: String?,
    onAll: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { FilterChip(stringResource(R.string.today_filter_all), selected == null, onAll) }
        items(activities, key = { it }) { name -> FilterChip(name, selected == name) { onSelect(name) } }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    Text(
        label,
        style = atxText(13.sp, FontWeight.SemiBold),
        color = if (isSelected) Color.White else colors.appPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) colors.appPrimary else colors.cardBackground.copy(alpha = 0.65f))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** One small muted line — the ghost cards below are the call to action. */
@Composable
private fun EmptyLine(activityFilter: String?) {
    Text(
        if (activityFilter != null) stringResource(R.string.today_empty_filtered, activityFilter)
        else stringResource(R.string.today_empty),
        style = atxText(14.sp, FontWeight.Medium),
        color = AtxTheme.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun ClaimedBanner(message: String) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .shadow(8.dp, shape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f))
            .clip(shape)
            .background(colors.appPrimary)
            .padding(horizontal = 20.dp, vertical = 13.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_how_to_reg), contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(message, style = atxText(14.sp, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun OpenSlotsSection(state: TodayUiState) {
    val colors = AtxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(
                when (state.openSlotsHeader) {
                    OpenSlotsHeader.OR_POST_YOUR_OWN -> R.string.today_header_or_post_your_own
                    OpenSlotsHeader.FREE_IN_THE_NEXT_DAY -> R.string.today_header_free_next_day
                    OpenSlotsHeader.YOUR_OPEN_SLOTS -> R.string.today_header_your_open_slots
                },
            ),
            style = atxText(15.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(top = 4.dp),
        )

        for (ranked in state.openSlots) {
            val slot = ranked.slot
            GhostSlotCard(
                titleLine = TodayFeed.timeUntilLine(slot.start, state.now, state.zone),
                activityName = slot.activityName,
                accessibilityLabel = stringResource(
                    R.string.today_ghost_a11y,
                    slot.activityName,
                    TodayFeed.accessibleTimeDescription(slot.start, state.now, state.zone),
                ),
            )
        }

        state.nextUsualSlot?.let { next ->
            // Opens Upcoming on iOS; disabled this round.
            Row(
                Modifier.padding(top = 2.dp).semantics { role = Role.Button; disabled() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    TodayFeed.nextUsualSlotLine(next.dayOfWeek.raw.orEmpty(), next.timeSlot.raw.orEmpty()),
                    style = atxText(13.sp, FontWeight.Medium),
                    color = colors.textTertiary,
                )
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
            }
        }

        Text(
            stringResource(R.string.today_start_from_scratch),
            style = atxText(14.sp, FontWeight.SemiBold),
            color = colors.appPrimary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
                .semantics { role = Role.Button; disabled() },
        )
    }
}
