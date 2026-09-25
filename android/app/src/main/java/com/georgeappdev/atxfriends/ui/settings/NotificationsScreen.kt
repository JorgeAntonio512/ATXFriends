package com.georgeappdev.atxfriends.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.BuildConfig
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/** The system notification permission, in iOS's terms. */
private enum class NotificationStatus { ENABLED, DISABLED, NOT_SET }

/** Port of iOS NotificationSettingsView. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AtxTheme.colors
    val context = LocalContext.current

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val status = remember(refresh) { notificationStatus(context) }
    LaunchedEffect(status) { Log.i("ATXF", "notifications: permission status = $status") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // React to the answer itself, not only to the screen resuming afterwards.
        Log.i("ATXF", "notifications: prompt answered, granted=$granted")
        refresh++
    }
    // Like iOS, the toggles only work once the system allows notifications.
    val canToggle = status == NotificationStatus.ENABLED

    SettingsSubScreen(title = stringResource(R.string.notif_title), onBack = onBack) {
        if (state.isLoading) {
            CircularProgressIndicator(color = colors.appPrimary, modifier = Modifier.align(Alignment.Center))
            return@SettingsSubScreen
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatusCard(status)
                when (status) {
                    NotificationStatus.DISABLED -> ActionCard(
                        title = stringResource(R.string.notif_disabled_title),
                        body = stringResource(R.string.notif_disabled_body),
                        icon = R.drawable.ic_set_gear,
                        button = stringResource(R.string.settings_open_settings),
                        onClick = { openNotificationSettings(context) },
                    )
                    NotificationStatus.NOT_SET -> ActionCard(
                        title = stringResource(R.string.notif_enable_title),
                        body = stringResource(R.string.notif_enable_body),
                        icon = R.drawable.ic_set_bell_active,
                        button = stringResource(R.string.notif_enable_title),
                        onClick = {
                            NotificationPermissionPrefs.markAsked(context)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Log.i("ATXF", "notifications: showing the system prompt")
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                    NotificationStatus.ENABLED -> Unit
                }
            }

            if (state.loadFailed) SettingsErrorBanner(stringResource(R.string.notif_load_failed), Modifier.padding(horizontal = 20.dp))
            if (state.saveFailed) {
                SettingsErrorBanner(stringResource(R.string.notif_save_failed), Modifier.padding(horizontal = 20.dp), onRetry = viewModel::retrySave)
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.notif_types_header), style = atxText(13.sp, FontWeight.SemiBold), color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.7f)).padding(20.dp),
                ) {
                    val rows = listOf(
                        Triple(NotificationToggle.NEW_MATCHES, R.drawable.ic_people, R.string.notif_new_matches to R.string.notif_new_matches_desc),
                        Triple(NotificationToggle.MESSAGES, R.drawable.ic_tab_messages_selected, R.string.notif_messages to R.string.notif_messages_desc),
                        Triple(NotificationToggle.PLAN_REQUESTS, R.drawable.ic_msg_calendar_plus, R.string.notif_plan_requests to R.string.notif_plan_requests_desc),
                        Triple(NotificationToggle.PLAN_CONFIRMATIONS, R.drawable.ic_msg_check_circle, R.string.notif_plan_confirmations to R.string.notif_plan_confirmations_desc),
                    )
                    rows.forEachIndexed { i, (toggle, icon, text) ->
                        if (i > 0) RowDivider(start = 52.dp)
                        ToggleRow(
                            icon = icon,
                            title = stringResource(text.first),
                            description = stringResource(text.second),
                            checked = state.isOn(toggle),
                            enabled = canToggle,
                            onChange = { viewModel.setToggle(toggle, it) },
                        )
                    }
                }
                Text(stringResource(R.string.notif_footer), style = atxText(12.sp), color = colors.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
            }

            if (BuildConfig.DEBUG) {
                // iOS DEBUG shows the FCM token; Android doesn't register one until push is built.
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DEBUG INFO", style = atxText(13.sp, FontWeight.SemiBold), color = colors.secondaryText, modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        "No FCM token yet",
                        style = atxText(12.sp).copy(fontFamily = FontFamily.Monospace),
                        color = colors.secondaryText,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.7f)).padding(20.dp),
                    )
                }
            }
        }

        if (state.isSaving) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.appPrimary)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings_saving), style = atxText(15.sp, FontWeight.Medium), color = Color.White)
            }
        }
    }
}

@Composable
private fun StatusCard(status: NotificationStatus) {
    val colors = AtxTheme.colors
    val (icon, tint, label) = when (status) {
        NotificationStatus.ENABLED -> Triple(R.drawable.ic_set_bell_active, colors.appPrimary, R.string.notif_status_enabled)
        NotificationStatus.DISABLED -> Triple(R.drawable.ic_set_bell_off, colors.danger, R.string.notif_status_disabled)
        NotificationStatus.NOT_SET -> Triple(R.drawable.ic_set_bell, SettingsColors.orange, R.string.notif_status_not_set)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.7f)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.notif_status_title), style = atxText(18.sp, FontWeight.Bold), color = colors.textStrong)
            Text(stringResource(label), style = atxText(15.sp, FontWeight.Medium), color = tint)
        }
    }
}

@Composable
private fun ActionCard(title: String, body: String, @DrawableRes icon: Int, button: String, onClick: () -> Unit) {
    val colors = AtxTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.cardBackground.copy(alpha = 0.7f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = atxText(16.sp, FontWeight.SemiBold), color = colors.textStrong)
        Text(body, style = atxText(14.sp), color = colors.secondaryText)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.appPrimary)
                .clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(button, style = atxText(16.sp, FontWeight.SemiBold), color = Color.White)
        }
    }
}

/** iOS InteractiveNotificationToggleRow. */
@Composable
private fun ToggleRow(@DrawableRes icon: Int, title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AtxTheme.colors
    val dim = Color.Gray.copy(alpha = 0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = if (enabled) colors.appPrimary else dim, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atxText(16.sp, FontWeight.SemiBold), color = if (enabled) colors.textStrong else Color.Gray.copy(alpha = 0.7f))
            Text(description, style = atxText(13.sp), color = colors.secondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.appPrimary, checkedThumbColor = Color.White),
        )
    }
}

private fun notificationStatus(context: Context): NotificationStatus {
    val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return if (allowed) NotificationStatus.ENABLED else NotificationStatus.DISABLED
    }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return when {
        granted && allowed -> NotificationStatus.ENABLED
        granted || NotificationPermissionPrefs.wasAsked(context) -> NotificationStatus.DISABLED
        else -> NotificationStatus.NOT_SET
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

/** Remembers (on this device) that the app has shown the notification prompt. */
internal object NotificationPermissionPrefs {
    private const val FILE = "settings"
    private const val KEY = "askedNotificationPermission"

    fun markAsked(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) }

    fun wasAsked(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
}
