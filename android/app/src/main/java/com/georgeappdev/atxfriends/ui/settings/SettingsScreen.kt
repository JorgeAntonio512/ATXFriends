package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.georgeappdev.atxfriends.BuildConfig
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.placeholder.PlaceholderScreen
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import com.google.firebase.FirebaseApp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        PlaceholderScreen(stringResource(R.string.tab_settings))
        if (BuildConfig.DEBUG) {
            DebugFirebaseProjectLine(Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** Debug builds only: which Firebase project this build talks to. Reads config, not data. */
@Composable
private fun DebugFirebaseProjectLine(modifier: Modifier = Modifier) {
    val projectId = remember {
        runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
    }
    Text(
        text = stringResource(
            R.string.debug_firebase_project,
            projectId ?: stringResource(R.string.debug_firebase_unavailable),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = AtxTheme.colors.textMuted,
        modifier = modifier,
    )
}
