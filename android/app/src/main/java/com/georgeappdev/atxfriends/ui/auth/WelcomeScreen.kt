package com.georgeappdev.atxfriends.ui.auth

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.AtxLogoMark
import com.georgeappdev.atxfriends.ui.components.AtxSecondaryButton
import com.georgeappdev.atxfriends.ui.components.AtxWordmark
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS OnboardingView, shown whenever nobody is signed in. This round it offers only
 * the email Sign In path: Register and the Apple/Google buttons are intentionally absent.
 */
@Composable
fun WelcomeScreen(onSignIn: () -> Unit) {
    val colors = AtxTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .safeDrawingPadding()
    ) {
        // Fill the screen when content is short (button at the bottom), scroll when it isn't.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            AtxLogoMark(icon = R.drawable.ic_people, size = 100.dp, iconSize = 42.dp)
            Spacer(Modifier.height(12.dp))
            AtxWordmark(fontSize = 42.sp)
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.welcome_tagline),
                style = atxText(24.sp, FontWeight.SemiBold),
                color = colors.textStrong,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_subtitle),
                style = atxText(16.sp),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureRow(R.drawable.ic_people, R.string.welcome_feature_friendship)
                FeatureRow(R.drawable.ic_tab_upcoming, R.string.welcome_feature_matching)
                FeatureRow(R.drawable.ic_location, R.string.welcome_feature_neighbors)
            }

            Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
            AtxSecondaryButton(text = stringResource(R.string.action_sign_in), onClick = onSignIn)
        }
    }
}

@Composable
private fun FeatureRow(@DrawableRes icon: Int, @StringRes text: Int) {
    val colors = AtxTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(26.dp))
        }
        Text(stringResource(text), style = atxText(15.sp, FontWeight.Medium), color = colors.textBody)
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    AtxTheme { WelcomeScreen(onSignIn = {}) }
}
