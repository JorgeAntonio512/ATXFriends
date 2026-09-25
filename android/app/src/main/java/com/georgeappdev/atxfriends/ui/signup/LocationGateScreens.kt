package com.georgeappdev.atxfriends.ui.signup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.location.LocationPermissionMonitor
import com.georgeappdev.atxfriends.ui.components.AtxLabeledField
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import kotlinx.coroutines.delay

private const val TAG = "ATXF"

/**
 * The whole gate: LocationGateView, and the LocationPermissionNeededView / WaitlistView it
 * leads to. [onPassed] / [onDismissed] are the email path's exits (a pending Google account
 * routes through SessionManager instead, so they're no-ops there).
 */
@Composable
fun LocationGateFlow(
    viewModel: LocationGateViewModel,
    permission: LocationPermissionMonitor,
    onPassed: (Coordinate) -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission.refresh("permissionDialog")
        viewModel.onPermissionResult(granted)
    }
    LaunchedEffect(viewModel) {
        viewModel.requestPermission.collect { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }
    LaunchedEffect(state.passedAt) {
        state.passedAt?.let {
            viewModel.passConsumed()
            onPassed(it)
        }
    }
    LaunchedEffect(state.dismissed) { if (state.dismissed) onDismissed() }

    // One of several permission signals (see LocationPermissionMonitor) — never the only one.
    LifecycleResumeEffect(permission) {
        permission.refresh("resume")
        onPauseOrDispose { }
    }

    // iOS hides the back button on all three screens; system back does what their text button does.
    BackHandler(enabled = !state.isFinishing) { viewModel.dismissAll() }

    when (state.screen) {
        GateScreen.GATE -> GateContent(
            state = state,
            onContinue = {
                permission.refresh("continue")
                viewModel.onContinue(permission.granted.value)
            },
            onBack = viewModel::dismissAll,
        )
        GateScreen.PERMISSION_NEEDED -> PermissionNeededContent(permission, isBusy = state.isFinishing, onNotNow = viewModel::dismissAll)
        GateScreen.LOCATION_OFF -> PermissionNeededContent(permission, isBusy = state.isFinishing, onNotNow = viewModel::dismissAll, locationOff = true)
        GateScreen.WAITLIST -> WaitlistContent(
            state = state,
            onEmailChange = viewModel::onWaitlistEmailChange,
            onSubmit = viewModel::submitWaitlist,
            onDismiss = viewModel::dismissAll,
        )
    }

    state.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.account_error_title)) },
            text = {
                Text(
                    stringResource(
                        if (failure == GateFailure.CREATE_FAILED) R.string.account_error_create_failed
                        else R.string.account_error_cancel_failed
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::retryFailure, enabled = !state.isFinishing) {
                    Text(stringResource(R.string.action_try_again))
                }
            },
            dismissButton = if (failure == GateFailure.CREATE_FAILED) {
                {
                    TextButton(onClick = viewModel::cancelAfterFailure, enabled = !state.isFinishing) {
                        Text(stringResource(R.string.action_cancel), color = AtxTheme.colors.danger)
                    }
                }
            } else null,
        )
    }
}

@Composable
private fun GateContent(state: GateUiState, onContinue: () -> Unit, onBack: () -> Unit) {
    val colors = AtxTheme.colors
    PinnedBottomLayout(
        content = { modifier ->
            Column(
                modifier.statusBarsPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                HeroIcon(R.drawable.ic_location)
                HeroText(stringResource(R.string.gate_title), stringResource(R.string.gate_body))
                Column(
                    Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.appPrimary.copy(alpha = 0.1f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_set_shield), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.gate_privacy_title), style = atxText(14.sp, FontWeight.SemiBold), color = colors.textBody)
                    }
                    Text(
                        stringResource(R.string.gate_privacy_body),
                        style = atxText(13.sp),
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        },
        bottomBar = {
            if (state.locationFailed) {
                Text(
                    stringResource(R.string.gate_timeout),
                    style = atxText(13.sp, FontWeight.Medium),
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            NavyButton(
                text = stringResource(R.string.action_continue),
                icon = R.drawable.ic_location,
                isLoading = state.isBusy,
                onClick = onContinue,
                modifier = Modifier.semantics { if (state.isChecking) liveRegion = LiveRegionMode.Polite },
            )
            TextLinkButton(stringResource(R.string.action_back), onClick = onBack, enabled = !state.isFinishing)
        },
    )
}

/**
 * Port of LocationPermissionNeededView (denied — "not a rejection"). While this is on screen the
 * permission is re-checked every second and whenever Settings closes, so allowing location in
 * Settings resumes the check with no extra tap.
 */
@Composable
private fun PermissionNeededContent(
    permission: LocationPermissionMonitor,
    isBusy: Boolean,
    onNotNow: () -> Unit,
    /** True: the phone's Location switch is off (open Location settings, not the app's). */
    locationOff: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permission.refresh("settingsReturned")
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                permission.refresh("poll")
                delay(1_000)
            }
        }
    }
    PinnedBottomLayout(
        content = { modifier ->
            Column(
                modifier.statusBarsPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                HeroIcon(R.drawable.ic_location_off)
                HeroText(
                    stringResource(if (locationOff) R.string.location_off_title else R.string.permission_needed_title),
                    stringResource(if (locationOff) R.string.location_off_body else R.string.permission_needed_body),
                    titleSize = 28,
                )
                Spacer(Modifier.height(20.dp))
            }
        },
        bottomBar = {
            NavyButton(
                text = stringResource(R.string.permission_needed_open_settings),
                icon = R.drawable.ic_set_gear,
                enabled = !isBusy,
                onClick = {
                    Log.i(TAG, "permission: opening ${if (locationOff) "Location" else "app"} settings")
                    val intent = if (locationOff) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    runCatching { settingsLauncher.launch(intent) }
                        .onFailure { Log.w(TAG, "permission: couldn't open app settings", it) }
                },
            )
            TextLinkButton(stringResource(R.string.action_not_now), onClick = onNotNow, enabled = !isBusy)
        },
    )
}

/** Port of WaitlistView (failed the 50-mile check). No account is created here. */
@Composable
private fun WaitlistContent(
    state: GateUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AtxTheme.colors
    val focus = LocalFocusManager.current
    PinnedBottomLayout(
        content = { modifier ->
            Column(
                modifier.statusBarsPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                HeroIcon(R.drawable.ic_location)
                HeroText(stringResource(R.string.waitlist_title), stringResource(R.string.waitlist_body), titleSize = 26)
                if (state.waitlistSubmitted) {
                    Column(
                        Modifier.padding(horizontal = 40.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_msg_check_circle), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.waitlist_success_title), style = atxText(20.sp, FontWeight.SemiBold), color = colors.primaryText)
                        Text(stringResource(R.string.waitlist_success_body), style = atxText(15.sp), color = colors.secondaryText, textAlign = TextAlign.Center)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtxLabeledField(
                            label = stringResource(R.string.field_email),
                            value = state.waitlistEmail,
                            onValueChange = onEmailChange,
                            placeholder = stringResource(R.string.field_email_placeholder),
                            icon = R.drawable.ic_mail,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focus.clearFocus()
                                onSubmit()
                            }),
                        )
                        if (state.waitlistFailed) {
                            Text(
                                stringResource(R.string.waitlist_failed),
                                style = atxText(13.sp, FontWeight.Medium),
                                color = colors.danger,
                                modifier = Modifier.padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        },
        bottomBar = {
            if (!state.waitlistSubmitted) {
                NavyButton(
                    text = stringResource(R.string.waitlist_submit),
                    isLoading = state.waitlistSubmitting,
                    enabled = state.waitlistEmailValid,
                    onClick = {
                        focus.clearFocus()
                        onSubmit()
                    },
                )
            }
            TextLinkButton(
                stringResource(if (state.waitlistSubmitted) R.string.action_done else R.string.action_not_now),
                onClick = onDismiss,
                enabled = !state.isFinishing && !state.waitlistSubmitting,
            )
        },
    )
}
