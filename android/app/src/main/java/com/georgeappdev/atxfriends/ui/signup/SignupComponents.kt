package com.georgeappdev.atxfriends.ui.signup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * The layout every gate / setup screen shares on iOS: scrolling content above a pinned bottom
 * bar (card color at 95%, soft upward shadow) holding the main button and a text button.
 */
@Composable
fun PinnedBottomLayout(
    content: @Composable (Modifier) -> Unit,
    bottomBar: @Composable ColumnScope.() -> Unit,
) {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxSize().background(colors.appBackground).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content(Modifier.fillMaxSize()) }
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(0.dp), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
                .background(colors.cardBackground.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = bottomBar,
        )
    }
}

/** The 160 pt pale circle + 110 pt burnt-orange circle + white 48 pt symbol. */
@Composable
fun HeroIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    val primary = AtxTheme.colors.appPrimary
    Box(modifier.size(160.dp).clip(CircleShape).background(primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(110.dp)
                .shadow(20.dp, CircleShape, ambientColor = primary.copy(alpha = 0.3f), spotColor = primary.copy(alpha = 0.3f))
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

/** Centered title + body, 40 pt side padding. */
@Composable
fun HeroText(title: String, body: String, titleSize: Int = 30) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = atxText(titleSize.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
        Text(body, style = atxText(17.sp).copy(lineHeight = 23.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
    }
}

/**
 * iOS navy call-to-action: 56 pt, 16 pt corners, optional leading symbol, spinner while busy.
 * Disabled buttons fade to half opacity (WaitlistView) and ignore taps.
 */
@Composable
fun NavyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    loadingText: String? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val navy = AtxTheme.colors.appNavy
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(12.dp, shape, ambientColor = navy.copy(alpha = 0.3f), spotColor = navy.copy(alpha = 0.3f))
            .clip(shape)
            .background(if (enabled) navy else navy.copy(alpha = 0.5f))
            .clickable(enabled = enabled && !isLoading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                if (loadingText != null) Text(loadingText, style = atxText(18.sp, FontWeight.SemiBold), color = Color.White)
            } else {
                if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text, style = atxText(18.sp, FontWeight.SemiBold), color = Color.White)
                if (trailingIcon != null) Icon(painterResource(trailingIcon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Profile setup's "Continue": burnt orange when allowed, gray when not. */
@Composable
fun SetupContinueButton(text: String, enabled: Boolean, onClick: () -> Unit, disabledAlpha: Float = 0.3f) {
    val colors = AtxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (enabled) Modifier.shadow(12.dp, shape, ambientColor = colors.appPrimary.copy(alpha = 0.3f), spotColor = colors.appPrimary.copy(alpha = 0.3f))
                else Modifier
            )
            .clip(shape)
            .background(if (enabled) colors.appPrimary else Color.Gray.copy(alpha = disabledAlpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = atxText(18.sp, FontWeight.SemiBold), color = Color.White)
    }
}

/** "Back" / "Not now" / "Done": plain burnt-orange text. */
@Composable
fun TextLinkButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text,
        style = atxText(16.sp, FontWeight.Medium),
        color = AtxTheme.colors.appPrimary.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
