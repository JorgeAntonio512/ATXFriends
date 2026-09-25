package com.georgeappdev.atxfriends.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.navigation.AppTab
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of the iOS `CustomTabBar`: a 1dp top border with a 3dp burnt-orange indicator that
 * slides to the selected tab, and six equal-width icon + label items. Tabs in [badgedTabs]
 * show iOS's 8dp red dot at the icon's top-right corner.
 */
@Composable
fun AtxTabBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    badgedTabs: Set<AppTab> = emptySet(),
) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.cardBackground)
            .navigationBarsPadding()
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp)) {
            val tabWidth = maxWidth / AppTab.entries.size
            val indicatorOffset by animateDpAsState(tabWidth * selectedTab.ordinal, label = "tabIndicator")
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(3.dp)
                    .background(colors.appPrimary)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 6.dp)
                .selectableGroup(),
        ) {
            AppTab.entries.forEach { tab ->
                AtxTabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    hasBadge = tab in badgedTabs,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AtxTabItem(
    tab: AppTab,
    selected: Boolean,
    hasBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeDescription = stringResource(R.string.tab_badge_unread)
    val tint = if (selected) AtxTheme.colors.appPrimary else AtxTheme.colors.secondaryText
    val labelStyle = MaterialTheme.typography.labelSmall
    Column(
        modifier = modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { if (hasBadge) stateDescription = badgeDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            Icon(
                painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            if (hasBadge) {
                // iOS: Circle().fill(.red) 8x8, offset (12, -4) from the icon's top-trailing corner.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-4).dp)
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
        // Single line always; only shrinks below 10sp if a very large font scale
        // would otherwise clip the label on a narrow phone.
        BasicText(
            text = stringResource(tab.label),
            style = labelStyle.copy(textAlign = TextAlign.Center),
            color = { tint },
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = labelStyle.fontSize),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}
