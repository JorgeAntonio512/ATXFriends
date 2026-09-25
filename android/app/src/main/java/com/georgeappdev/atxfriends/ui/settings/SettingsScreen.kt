package com.georgeappdev.atxfriends.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.BuildConfig
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.placeholder.PlaceholderScreen
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import com.google.firebase.FirebaseApp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        PlaceholderScreen(stringResource(R.string.tab_settings))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (BuildConfig.DEBUG) DebugFirebaseProjectLine()
            state.email?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AtxTheme.colors.textMuted)
            }
            state.displayName?.let {
                Text(it, style = atxText(16.sp, FontWeight.Medium), color = AtxTheme.colors.primaryText)
            }
            SignOutButton(onClick = { confirmSignOut = true })
        }
    }

    if (confirmSignOut) {
        // Same wording and buttons as the iOS "Sign Out" alert.
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.sign_out), color = AtxTheme.colors.danger) }
            },
        )
    }
}

/** iOS Settings sign-out row: logout icon + "Sign Out" in the danger color. */
@Composable
private fun SignOutButton(onClick: () -> Unit) {
    val danger = AtxTheme.colors.danger
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = danger, modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.sign_out), style = atxText(16.sp, FontWeight.Medium), color = danger)
    }
}

/** Debug builds only: which Firebase project this build talks to. Reads config, not data. */
@Composable
private fun DebugFirebaseProjectLine() {
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
    )
}
