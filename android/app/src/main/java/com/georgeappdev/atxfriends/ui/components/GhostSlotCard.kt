package com.georgeappdev.atxfriends.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Today's ghost cards read as "happening now" (solid); Upcoming's as "not posted yet" (dashed). */
enum class GhostCardStyle { SOLID, DASHED }

private val CardShape = RoundedCornerShape(16.dp)

/**
 * Port of iOS GhostSlotCardView, the one open-slot card Today and Upcoming share: bold title,
 * orange "Activity?", and a filled pill button. Only the pill is tappable, as on iOS; screen
 * readers get the whole card as one button with [accessibilityLabel].
 */
@Composable
fun GhostSlotCard(
    titleLine: String,
    activityLine: String,
    style: GhostCardStyle,
    @DrawableRes buttonIcon: Int,
    buttonLabel: String,
    accessibilityLabel: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AtxTheme.colors
    val base = when (style) {
        GhostCardStyle.SOLID -> modifier
            .fillMaxWidth()
            .shadow(8.dp, CardShape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(CardShape)
            .background(colors.cardBackground.copy(alpha = 0.85f))
        GhostCardStyle.DASHED -> {
            val border = colors.appPrimary.copy(alpha = 0.45f)
            modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(colors.cardBackground.copy(alpha = 0.35f))
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
                    )
                }
        }
    }
    Column(
        base
            .padding(16.dp)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                onClick { onTap(); true }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(titleLine, style = atxText(16.sp, FontWeight.Bold), color = colors.primaryText)
        Text(activityLine, style = atxText(14.sp, FontWeight.SemiBold), color = colors.appPrimary)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.appPrimary)
                    .clickable(onClick = onTap)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(painterResource(buttonIcon), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Text(buttonLabel, style = atxText(15.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}
