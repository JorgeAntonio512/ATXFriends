package com.georgeappdev.atxfriends.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS PendingMatchCard (300pt wide). Yay/Nay decide right away with no confirm alert,
 * as on iOS. [savingDecision] disables both buttons while any decision is saving;
 * [isThisCardSaving] adds a spinner to this card.
 */
@Composable
fun PendingMatchCard(
    match: MatchUi,
    onTap: () -> Unit,
    onDecide: (yay: Boolean) -> Unit,
    savingDecision: Boolean,
    isThisCardSaving: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .width(300.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(colors.cardBackground)
    ) {
        Column(Modifier.clickable(role = Role.Button, onClick = onTap)) {
            RemotePhoto(
                url = match.firstPhotoURL,
                contentDescription = match.name,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                loading = { PhotoPlaceholder(iconSize = 80.dp, showSpinner = true) },
                fallback = { PhotoPlaceholder(iconSize = 80.dp, showSpinner = false) },
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(match.name, style = atxText(20.sp, FontWeight.Bold), color = colors.primaryText)
                match.distanceText?.let {
                    IconLabel(R.drawable.ic_location, it, 12.dp, colors.appPrimary, 13.sp, FontWeight.Medium, colors.secondaryText, spacing = 4.dp)
                }
                HorizontalDivider(color = colors.border)
                MatchReasons(match)
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DecisionButton(
                label = stringResource(R.string.matches_nay),
                icon = R.drawable.ic_thumb_down,
                background = colors.border.copy(alpha = 0.5f),
                content = colors.secondaryText,
                enabled = !savingDecision,
                showSpinner = false,
                onClick = { onDecide(false) },
                modifier = Modifier.weight(1f),
            )
            DecisionButton(
                label = stringResource(R.string.matches_yay),
                icon = R.drawable.ic_thumb_up,
                background = colors.appPrimary,
                content = Color.White,
                enabled = !savingDecision,
                showSpinner = isThisCardSaving,
                onClick = { onDecide(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Shared interests chips, the category label, and the "Free at the same time" chips. */
@Composable
private fun MatchReasons(match: MatchUi) {
    val colors = AtxTheme.colors
    if (match.sharedActivities.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconLabel(R.drawable.ic_logo_heart, stringResource(R.string.matches_shared_interests), 12.dp, colors.appPrimary, 13.sp, FontWeight.SemiBold, colors.secondaryText)
            ChipFlow(match.sharedActivities, 12.sp, FontWeight.Medium, 10.dp, 6.dp, 12.dp, 6.dp)
        }
    }
    match.sharedCategory?.let {
        IconLabel(R.drawable.ic_sparkles, stringResource(R.string.matches_category_label, it), 12.dp, colors.appPrimary, 13.sp, FontWeight.SemiBold, colors.secondaryText)
    }
    if (match.sharedTimes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconLabel(R.drawable.ic_tab_upcoming, stringResource(R.string.matches_free_same_time), 12.dp, colors.appPrimary, 13.sp, FontWeight.SemiBold, colors.secondaryText)
            ChipFlow(match.sharedTimes, 12.sp, FontWeight.Medium, 10.dp, 6.dp, 12.dp, 6.dp)
        }
    }
}

/** Yay/Nay in the iOS style. */
@Composable
private fun DecisionButton(
    label: String,
    icon: Int,
    background: Color,
    content: Color,
    enabled: Boolean,
    showSpinner: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(label, style = atxText(16.sp, FontWeight.SemiBold), color = content, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * Port of iOS ConnectedMatchRow. [onPlan] opens the plan composer; without it the Plan button
 * is inert. The Message button is disabled this round.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectedMatchRow(match: MatchUi, onTap: () -> Unit, modifier: Modifier = Modifier, onPlan: (() -> Unit)? = null) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemotePhoto(
            url = match.firstPhotoURL,
            contentDescription = match.name,
            modifier = Modifier.size(60.dp).clip(CircleShape),
            loading = { PhotoPlaceholder(iconSize = 40.dp, showSpinner = false) },
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    match.name,
                    style = atxText(17.sp, FontWeight.Bold),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(painterResource(R.drawable.ic_verified), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
            }
            sharedSummary(match)?.let { Text(it, style = atxText(14.sp), color = colors.secondaryText) }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                IconLabel(R.drawable.ic_calendar_add, match.showUpMeter, 12.dp, colors.appPrimary, 12.sp, FontWeight.SemiBold, colors.appPrimary, spacing = 4.dp)
                match.simpaticoScore?.let { SimpaticoBadge(it) }
                match.shortDistanceText?.let {
                    IconLabel(R.drawable.ic_location, it, 12.dp, colors.secondaryText, 12.sp, FontWeight.Medium, colors.secondaryText, spacing = 4.dp)
                }
            }
        }

        // Plan opens the composer (.proposal); Message is shown in the iOS style, disabled this round.
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.15f))
                .then(if (onPlan != null) Modifier.clickable(role = Role.Button, onClick = onPlan) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_calendar_add), contentDescription = stringResource(R.string.matches_plan_button), tint = colors.appPrimary, modifier = Modifier.size(22.dp))
        }
        Box(
            Modifier.size(44.dp).shadow(6.dp, CircleShape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f))
                .clip(CircleShape).background(colors.appPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tab_messages_selected), contentDescription = stringResource(R.string.matches_message_button), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun sharedSummary(match: MatchUi): String? {
    val first = match.sharedActivities.firstOrNull()
    return when {
        first != null && match.sharedActivities.size > 1 ->
            stringResource(R.string.matches_more_activities, first, match.sharedActivities.size - 1)
        first != null -> first
        match.sharedCategory != null -> stringResource(R.string.matches_category_label, match.sharedCategory)
        else -> null
    }
}

@Composable
private fun SimpaticoBadge(score: Int) {
    val primary = AtxTheme.colors.appPrimary
    Row(
        Modifier.clip(CircleShape).background(primary.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(painterResource(R.drawable.ic_tab_simpatico_selected), contentDescription = null, tint = primary, modifier = Modifier.size(11.dp))
        Text(stringResource(R.string.matches_simpatico_score, score), style = atxText(11.sp, FontWeight.SemiBold), color = primary, maxLines = 1)
    }
}

/** The "Connected" strip of small avatars pinned under the title (iOS connectedAvatarStrip). */
@Composable
fun ConnectedAvatarStrip(connected: List<MatchUi>, onTap: (String) -> Unit) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(colors.cardBackground)
            .padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.matches_connected_title), style = atxText(13.sp, FontWeight.SemiBold), color = colors.primaryText)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            connected.forEach { match ->
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onTap(match.matchID) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val avatarFallback: @Composable () -> Unit = {
                        Box(Modifier.size(44.dp).background(colors.appPrimary), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_person), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    RemotePhoto(
                        url = match.firstPhotoURL,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                        loading = avatarFallback,
                    )
                    Text(
                        match.name,
                        style = atxText(11.sp),
                        color = colors.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(60.dp),
                    )
                }
            }
        }
    }
}

/** Port of iOS EmptyMatchesView. */
@Composable
fun EmptyMatches(modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_heart_outline), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(60.dp))
        }
        Column(Modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.matches_empty_title), style = atxText(28.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.matches_empty_body),
                style = atxText(16.sp).copy(lineHeight = 22.sp),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.appPrimary.copy(alpha = 0.1f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconLabel(R.drawable.ic_sparkles, stringResource(R.string.matches_how_title), 18.dp, colors.appPrimary, 15.sp, FontWeight.SemiBold, colors.secondaryText, spacing = 8.dp)
            Text(stringResource(R.string.matches_how_body), style = atxText(14.sp), color = colors.secondaryText)
        }
    }
}
