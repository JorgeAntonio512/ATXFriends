package com.georgeappdev.atxfriends.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Fixed (non-adaptive) accent colors from AppColors.swift used by Privacy & Safety. */
internal object SettingsColors {
    val iconWarm = Color(red = 0.85f, green = 0.55f, blue = 0.40f)
    val iconInfo = Color(red = 0.45f, green = 0.60f, blue = 0.70f)
    val iconSafe = Color(red = 0.45f, green = 0.70f, blue = 0.45f)
    val dangerStrong = Color(red = 0.90f, green = 0.35f, blue = 0.35f)

    /** SwiftUI `.orange`, used by the "select more" warning banners. */
    val orange = Color(0xFFFF9500)
}

/**
 * A pushed Settings screen: an inline title bar with a back button (iOS `.inline` navigation
 * title), optional trailing action, then [content]. When [backEnabled] is false the back
 * button is greyed out and system back is swallowed, like iOS's hidden back button +
 * `interactiveDismissDisabled`.
 */
@Composable
fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AtxTheme.colors
    BackHandler(enabled = !backEnabled) { /* Blocked until the selection is valid. */ }
    Column(modifier.fillMaxSize().background(colors.appBackground)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = backEnabled, role = Role.Button, onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (backEnabled) colors.appPrimary else Color.Gray.copy(alpha = 0.5f)
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.settings_back), style = atxText(17.sp), color = tint)
            }
            Text(
                title,
                style = atxText(17.sp, FontWeight.SemiBold),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 220.dp).semantics { heading() },
            )
            if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
        }
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** The toolbar "Save" button with its spinner, as on EditProfileView / SearchRadiusSettingsView. */
@Composable
fun SaveAction(enabled: Boolean, isSaving: Boolean, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    if (isSaving) {
        Box(Modifier.padding(horizontal = 16.dp).size(22.dp)) {
            CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp)
        }
    } else {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(
                stringResource(R.string.settings_save),
                style = atxText(17.sp, FontWeight.SemiBold),
                color = if (enabled) colors.appPrimary else Color.Gray,
            )
        }
    }
}

/** iOS `SettingsCard`: uppercase section title above a rounded card. */
@Composable
fun SettingsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AtxTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(),
            style = atxText(13.sp, FontWeight.SemiBold),
            color = colors.secondaryText,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .padding(16.dp),
        ) { content() }
    }
}

/** iOS `SettingsRowLabel` (and the Location / Sign Out rows built the same way). */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = AtxTheme.colors.appPrimary,
    titleColor: Color = AtxTheme.colors.primaryText,
    showChevron: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atxText(16.sp, FontWeight.Medium), color = titleColor)
            if (subtitle != null) Text(subtitle, style = atxText(14.sp), color = colors.secondaryText)
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
        }
    }
}

/** A divider inset past the row icon (`Divider().padding(.leading, 44)`). */
@Composable
fun RowDivider(start: Dp = 44.dp) {
    HorizontalDivider(Modifier.padding(start = start), color = AtxTheme.colors.border.copy(alpha = 0.6f))
}

/** The tinted "Tips" / "How This Works" boxes: icon + title, then bullet lines. */
@Composable
fun InfoBox(@DrawableRes icon: Int, title: String, lines: List<String>, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.appPrimary.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(18.dp))
            Text(title, style = atxText(15.sp, FontWeight.SemiBold), color = colors.textBody)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { Text(it, style = atxText(14.sp), color = colors.secondaryText) }
        }
    }
}

/** The orange "Select … to continue" banner on Activities / Availability. */
@Composable
fun SelectionWarningBanner(title: String, detail: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SettingsColors.orange.copy(alpha = 0.15f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_set_warning), contentDescription = null, tint = SettingsColors.orange, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = atxText(15.sp, FontWeight.SemiBold), color = colors.textStrong)
            Text(detail, style = atxText(13.sp), color = colors.secondaryText)
        }
    }
}

/** Android-only visible error for failures iOS only logs. Optional retry. */
@Composable
fun SettingsErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.danger.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
        Text(message, style = atxText(14.sp, FontWeight.Medium), color = colors.textStrong, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_try_again), style = atxText(14.sp, FontWeight.SemiBold), color = colors.appPrimary)
            }
        }
    }
}

/** The small floating "Saving..." card iOS overlays while autosaving. */
@Composable
fun SavingIndicator(text: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(
        modifier
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(text, style = atxText(15.sp, FontWeight.Medium), color = colors.primaryText)
    }
}

/** Big circular header icon (EditProfileView / SearchRadiusSettingsView). */
@Composable
fun HeaderIconCircle(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Box(
        modifier.size(100.dp).clip(CircleShape).background(colors.appPrimary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(60.dp))
    }
}

/** Screen headline + subtitle used at the top of most Settings screens. */
@Composable
fun ScreenHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = atxText(28.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
        Text(subtitle, style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/** Centered spinner with a caption ("Loading matches..."). */
@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = colors.appPrimary)
        Text(text, style = atxText(16.sp, FontWeight.Medium), color = colors.secondaryText, modifier = Modifier.padding(top = 16.dp))
    }
}

/** Centered icon + title + body (empty lists). */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(60.dp))
        Text(title, style = atxText(24.sp, FontWeight.Bold), color = colors.textStrong, textAlign = TextAlign.Center)
        Text(body, style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}
