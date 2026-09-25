package com.georgeappdev.atxfriends.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.BuildConfig
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.domain.location.LastUpdated
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme
import com.google.firebase.FirebaseApp
import java.time.Instant
import java.time.ZoneId

/** Port of iOS SettingsTabView: Profile, Preferences, Share My Location and Account cards. */
@Composable
fun SettingsScreen(
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val context = LocalContext.current
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.onAppear() }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // iOS large navigation title.
        Text(
            stringResource(R.string.settings_title),
            style = atxText(34.sp, FontWeight.Bold),
            color = colors.primaryText,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp).semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsCard(stringResource(R.string.settings_section_profile)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsRow(R.drawable.ic_person_circle, stringResource(R.string.settings_row_display_name), { onNavigate(SettingsEditName) }, subtitle = stringResource(R.string.settings_row_display_name_sub))
                    RowDivider()
                    SettingsRow(R.drawable.ic_set_photo, stringResource(R.string.settings_row_photos), { onNavigate(SettingsPhotos) }, subtitle = stringResource(R.string.settings_row_photos_sub))
                    RowDivider()
                    SettingsRow(R.drawable.ic_set_heart, stringResource(R.string.settings_row_activities), { onNavigate(SettingsActivities) }, subtitle = stringResource(R.string.settings_row_activities_sub))
                    RowDivider()
                    SettingsRow(R.drawable.ic_tab_upcoming, stringResource(R.string.settings_row_availability), { onNavigate(SettingsAvailability) }, subtitle = stringResource(R.string.settings_row_availability_sub))
                }
            }

            SettingsCard(stringResource(R.string.settings_section_preferences)) {
                SettingsRow(
                    R.drawable.ic_location,
                    stringResource(R.string.settings_row_radius),
                    { onNavigate(SettingsRadius) },
                    subtitle = stringResource(R.string.settings_row_radius_sub, state.radiusMiles),
                )
            }

            SettingsCard(stringResource(R.string.settings_section_share_location)) {
                ShareMyLocationContent(state, viewModel)
            }

            SettingsCard(stringResource(R.string.settings_section_account)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsRow(R.drawable.ic_set_bell, stringResource(R.string.settings_row_notifications), { onNavigate(SettingsNotifications) }, subtitle = stringResource(R.string.settings_row_notifications_sub))
                    RowDivider()
                    SettingsRow(R.drawable.ic_set_hand, stringResource(R.string.settings_row_privacy), { onNavigate(SettingsPrivacy) }, subtitle = stringResource(R.string.settings_row_privacy_sub))
                    RowDivider()
                    LocationAccessRow(onClick = { openAppSettings(context) })
                    RowDivider()
                    SettingsRow(
                        R.drawable.ic_logout,
                        stringResource(R.string.sign_out),
                        { confirmSignOut = true },
                        iconTint = colors.danger,
                        titleColor = colors.danger,
                    )
                }
            }

            // App info footer.
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.settings_footer_app), style = atxText(14.sp, FontWeight.SemiBold), color = colors.secondaryText)
                Text(stringResource(R.string.settings_footer_version), style = atxText(12.sp), color = colors.secondaryText)
                if (BuildConfig.DEBUG) DebugFirebaseProjectLine()
            }
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
                }) { Text(stringResource(R.string.sign_out), color = colors.danger) }
            },
        )
    }
}

/** iOS ShareMyLocationContent: mode picker, explainer, last-updated line, "Update now". */
@Composable
private fun ShareMyLocationContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    val colors = AtxTheme.colors
    val context = LocalContext.current
    val share = state.share

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    // Launched once per request: after a rotation the pending result is delivered to the
    // recreated launcher, so launching again would turn the user's answer into a denial.
    var launchedFor by rememberSaveable { mutableStateOf<LocationSharingMode?>(null) }
    LaunchedEffect(share.permissionRequestFor) {
        val request = share.permissionRequestFor
        if (request == null) {
            launchedFor = null
        } else if (launchedFor != request) {
            launchedFor = request
            LocationPermissionPrefs.markAsked(context)
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedPicker(
            options = listOf(
                LocationSharingMode.OFF to stringResource(R.string.share_mode_off),
                LocationSharingMode.ONCE to stringResource(R.string.share_mode_once),
                LocationSharingMode.ON_OPEN to stringResource(R.string.share_mode_on_open),
            ),
            selected = share.selected,
            enabled = !share.isUpdating,
            onSelect = viewModel::selectShareMode,
        )

        Text(stringResource(R.string.share_explainer), style = atxText(13.sp), color = colors.textTertiary)

        state.profile?.locationUpdatedAt?.let { updatedAt ->
            val label = when (LastUpdated.of(updatedAt, Instant.now(), ZoneId.systemDefault())) {
                LastUpdated.TODAY -> stringResource(R.string.share_today)
                LastUpdated.THIS_WEEK -> stringResource(R.string.share_this_week)
                LastUpdated.OVER_A_WEEK_AGO -> stringResource(R.string.share_over_week)
            }
            Text(stringResource(R.string.share_last_updated, label), style = atxText(13.sp, FontWeight.Medium), color = colors.textMuted)
        }

        if (share.selected == LocationSharingMode.ONCE) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.appPrimary.copy(alpha = 0.12f))
                    .selectable(selected = false, enabled = !share.isUpdating, role = Role.Button, onClick = viewModel::updateNow),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (share.isUpdating) {
                    CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.share_update_now), style = atxText(15.sp, FontWeight.SemiBold), color = colors.appPrimary)
                }
            }
        }

        if (share.saveFailed) {
            SettingsErrorBanner(stringResource(R.string.share_save_failed))
        }
    }

    if (share.showPermissionDenied) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionDenied,
            title = { Text(stringResource(R.string.share_permission_title)) },
            text = { Text(stringResource(R.string.share_permission_body)) },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPermissionDenied) { Text(stringResource(R.string.action_cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPermissionDenied()
                    openAppSettings(context)
                }) { Text(stringResource(R.string.settings_open_settings)) }
            },
        )
    }
}

/** An iOS-style segmented control. Labels wrap rather than truncate on narrow phones. */
@Composable
private fun <T> SegmentedPicker(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    val colors = AtxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.border.copy(alpha = 0.5f))
            .padding(2.dp)
            .selectableGroup(),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                Modifier
                    .weight(1f)
                    .then(if (isSelected) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)) else Modifier)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) colors.cardBackground else colors.cardBackground.copy(alpha = 0f))
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = atxText(13.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (enabled) colors.primaryText else colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** iOS "Location" row: status badge, opens the system settings for this app. */
@Composable
private fun LocationAccessRow(onClick: () -> Unit) {
    val colors = AtxTheme.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val badge = remember(refresh) { LocationBadge.current(context) }
    val (text, tint) = when (badge) {
        LocationBadge.PRECISE -> stringResource(R.string.settings_location_precise) to colors.appPrimary
        LocationBadge.APPROXIMATE -> stringResource(R.string.settings_location_approximate) to colors.appPrimary
        LocationBadge.DENIED -> stringResource(R.string.settings_location_denied) to colors.danger
        LocationBadge.NOT_SET -> stringResource(R.string.settings_location_not_set) to colors.secondaryText
    }
    SettingsRow(
        R.drawable.ic_location,
        stringResource(R.string.settings_row_location),
        onClick,
        subtitle = stringResource(R.string.settings_row_location_sub),
        showChevron = true,
        trailing = {
            Text(
                text,
                style = atxText(13.sp, FontWeight.SemiBold),
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        },
    )
}

private enum class LocationBadge {
    PRECISE, APPROXIMATE, DENIED, NOT_SET;

    companion object {
        fun current(context: Context): LocationBadge {
            val location = (context.applicationContext as AtxFriendsApp).container.deviceLocation
            return when {
                location.hasPreciseAccess() -> PRECISE
                location.hasPermission() -> APPROXIMATE
                LocationPermissionPrefs.wasAsked(context) -> DENIED
                else -> NOT_SET
            }
        }
    }
}

/**
 * Android can't tell "never asked" from "denied" without having asked, so this remembers
 * (on this device only) that the app has shown the location prompt.
 */
internal object LocationPermissionPrefs {
    private const val FILE = "settings"
    private const val KEY = "askedLocationPermission"

    fun markAsked(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) }

    fun wasAsked(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
}

/** The system settings page for this app (iOS `openSettingsURLString`). */
internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
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
