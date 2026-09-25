package com.georgeappdev.atxfriends.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.matches.IconLabel
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

private val CardShape = RoundedCornerShape(16.dp)

/** The solid Today card background shared by plan cards and ghost cards. */
@Composable
private fun Modifier.todayCard(): Modifier {
    val colors = AtxTheme.colors
    return this
        .fillMaxWidth()
        .shadow(8.dp, CardShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
        .clip(CardShape)
        .background(colors.cardBackground.copy(alpha = 0.85f))
        .padding(16.dp)
}

/** Port of iOS TodayPlanCard. "I'm in" is shown but disabled until claiming is built. */
@Composable
fun TodayPlanCard(plan: TodayPlanUi, timeBadge: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(modifier.todayCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(plan.activityName, style = atxText(18.sp, FontWeight.Bold), color = colors.primaryText, modifier = Modifier.weight(1f))
            Text(
                timeBadge,
                style = atxText(13.sp, FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.appPrimary)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            IconLabel(
                R.drawable.ic_person_circle, plan.posterName ?: stringResource(R.string.today_poster_unknown),
                13.dp, colors.appPrimary, 13.sp, FontWeight.Medium, colors.textBody, spacing = 5.dp,
            )
            Text(stringResource(R.string.today_dot), style = atxText(13.sp), color = colors.textSubtle)
            IconLabel(R.drawable.ic_verified, plan.showUpMeter, 11.dp, colors.appPrimary, 12.sp, FontWeight.SemiBold, colors.appPrimary, spacing = 5.dp)
        }

        plan.location?.let { location ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(12.dp))
                Text(location, style = atxText(13.sp, FontWeight.Medium), color = colors.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        plan.note?.let { note ->
            Text(
                note,
                style = atxText(13.sp).copy(fontStyle = FontStyle.Italic),
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (plan.isOwnPlan) {
                Text(stringResource(R.string.today_your_post), style = atxText(13.sp, FontWeight.Medium), color = colors.textFaint)
            } else {
                PillButton(R.drawable.ic_thumb_up, stringResource(R.string.today_im_in), withShadow = true)
            }
        }
    }
}

/**
 * Port of iOS GhostSlotCardView in its `.solid` (Today) style. "Post it" is shown but disabled
 * until posting is built.
 */
@Composable
fun GhostSlotCard(titleLine: String, activityName: String, accessibilityLabel: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .todayCard()
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                disabled()
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(titleLine, style = atxText(16.sp, FontWeight.Bold), color = colors.primaryText)
        Text(stringResource(R.string.today_ghost_activity, activityName), style = atxText(14.sp, FontWeight.SemiBold), color = colors.appPrimary)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            PillButton(R.drawable.ic_add, stringResource(R.string.today_post_it), withShadow = false)
        }
    }
}

/** The filled orange pill ("I'm in" / "Post it"), drawn in the iOS style but not clickable yet. */
@Composable
private fun PillButton(icon: Int, label: String, withShadow: Boolean) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val shadow = if (withShadow) {
        Modifier.shadow(6.dp, shape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f))
    } else {
        Modifier
    }
    Row(
        shadow
            .clip(shape)
            .background(colors.appPrimary)
            .semantics { role = Role.Button; disabled() }
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(label, style = atxText(15.sp, FontWeight.SemiBold), color = Color.White)
    }
}
