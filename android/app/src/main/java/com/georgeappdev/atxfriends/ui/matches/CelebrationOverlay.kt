package com.georgeappdev.atxfriends.ui.matches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of the iOS mutual-match celebration: 🎉, "You're connected!", the other person's name,
 * "Go say hi 👋", Open / Maybe later. Tapping outside the card dismisses it; the ViewModel
 * auto-dismisses it after 6 seconds.
 */
@Composable
fun CelebrationOverlay(name: String, onOpen: () -> Unit, onLater: () -> Unit) {
    val colors = AtxTheme.colors
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLater),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .padding(horizontal = 40.dp)
                    .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                    .clip(shape)
                    .background(colors.cardBackground)
                    // Swallow taps on the card so they don't dismiss it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(stringResource(R.string.celebration_emoji), fontSize = 48.sp)
                Text(stringResource(R.string.celebration_title), style = atxText(22.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
                Text(name, style = atxText(17.sp, FontWeight.Medium), color = colors.primaryText, textAlign = TextAlign.Center)
                Text(stringResource(R.string.celebration_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
                Column(
                    Modifier.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.appPrimary)
                            .clickable(role = Role.Button, onClick = onOpen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.celebration_open), style = atxText(17.sp, FontWeight.SemiBold), color = Color.White)
                    }
                    Text(
                        stringResource(R.string.celebration_later),
                        style = atxText(15.sp),
                        color = colors.secondaryText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button, onClick = onLater)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
