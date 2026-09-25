package com.georgeappdev.atxfriends.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.AtxLogoMark
import com.georgeappdev.atxfriends.ui.components.AtxPrimaryButton
import com.georgeappdev.atxfriends.ui.components.AtxSecondaryButton
import com.georgeappdev.atxfriends.ui.components.AtxWordmark
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** Port of iOS LoadingView: logo, "ATX Friends", spinner. */
@Composable
fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(AtxTheme.colors.appBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AtxLogoMark(icon = R.drawable.ic_logo_heart, size = 100.dp, iconSize = 44.dp)
            AtxWordmark(fontSize = 36.sp)
            CircularProgressIndicator(color = AtxTheme.colors.appPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

/** Signed in, but the account never finished signup. Android can't finish it yet. */
@Composable
fun AccountNotFinishedScreen(onSignOut: () -> Unit) {
    MessageScreen(
        title = stringResource(R.string.account_unfinished_title),
        body = stringResource(R.string.account_unfinished_body),
    ) {
        AtxPrimaryButton(text = stringResource(R.string.sign_out), onClick = onSignOut)
    }
}

/** Signed in, but the profile couldn't be read. Reuses the iOS network-error wording. */
@Composable
fun LoadFailedScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    MessageScreen(title = null, body = stringResource(R.string.auth_error_network)) {
        AtxPrimaryButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
        AtxSecondaryButton(text = stringResource(R.string.sign_out), onClick = onSignOut)
    }
}

@Composable
private fun MessageScreen(title: String?, body: String, actions: @Composable () -> Unit) {
    val colors = AtxTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AtxLogoMark(icon = R.drawable.ic_people, size = 80.dp, iconSize = 36.dp)
            if (title != null) {
                Text(title, style = atxText(24.sp, FontWeight.Bold), color = colors.primaryText, textAlign = TextAlign.Center)
            }
            Text(body, style = atxText(16.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

@Preview
@Composable
private fun AccountNotFinishedPreview() {
    AtxTheme { AccountNotFinishedScreen(onSignOut = {}) }
}
