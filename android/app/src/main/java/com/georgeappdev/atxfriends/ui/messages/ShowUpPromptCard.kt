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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS ShowUpPromptCard: after a claimed Today plan's time has passed, "How did it go?"
 * with "Didn't show" / "They showed up!". Both buttons are disabled while the report saves.
 */
@Composable
fun ShowUpPromptCard(
    otherUserName: String,
    activityName: String,
    isSaving: Boolean,
    onReport: (didShowUp: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().background(colors.promptCreamBg)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(PlanColors.promptGoldAccent))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_verified), null, tint = PlanColors.promptGold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.showup_title), style = atxText(13.sp, FontWeight.SemiBold), color = PlanColors.promptGold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isSaving, role = Role.Button, onClickLabel = stringResource(R.string.thread_dismiss), onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_msg_close), stringResource(R.string.thread_dismiss), tint = colors.secondaryText, modifier = Modifier.size(14.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.showup_passed, activityName), style = atxText(13.sp), color = colors.secondaryText)
                Text(stringResource(R.string.showup_question, otherUserName), style = atxText(17.sp, FontWeight.SemiBold), color = colors.primaryText)
            }

            Box(Modifier.padding(top = 2.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.alpha(if (isSaving) 0.5f else 1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReportButton(
                        icon = R.drawable.ic_thumb_down,
                        label = stringResource(R.string.showup_no),
                        content = PlanColors.declinedRed,
                        background = PlanColors.declinedRed.copy(alpha = 0.10f),
                        enabled = !isSaving,
                        onClick = { onReport(false) },
                        modifier = Modifier.weight(1f).clip(shape),
                    )
                    ReportButton(
                        icon = R.drawable.ic_thumb_up,
                        label = stringResource(R.string.showup_yes),
                        content = Color.White,
                        background = colors.appPrimary,
                        enabled = !isSaving,
                        onClick = { onReport(true) },
                        modifier = Modifier.weight(1f).clip(shape),
                    )
                }
                if (isSaving) CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ReportButton(
    icon: Int,
    label: String,
    content: Color,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(15.dp))
        Text(label, style = atxText(15.sp, FontWeight.SemiBold), color = content)
    }
}
