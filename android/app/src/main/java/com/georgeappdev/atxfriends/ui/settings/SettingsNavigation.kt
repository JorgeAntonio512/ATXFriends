package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// Screens pushed inside the Settings tab's nav graph.
@Serializable data object SettingsEditName
@Serializable data object SettingsPhotos
@Serializable data object SettingsActivities
@Serializable data object SettingsAvailability
@Serializable data object SettingsRadius
@Serializable data object SettingsNotifications
@Serializable data object SettingsPrivacy
@Serializable data object SettingsBlockUser
@Serializable data object SettingsBlockedUsers
@Serializable data object SettingsReportUser
@Serializable data object SettingsDeleteAccount
@Serializable data object SettingsExportData

/** Every screen reachable from the Settings tab, below its start screen. */
fun NavGraphBuilder.settingsDestinations(navController: NavHostController) {
    val back: NavBackStackEntry.() -> Unit = { ifResumed { navController.popBackStack() } }
    composable<SettingsEditName> { EditNameScreen(onBack = { it.back() }) }
    composable<SettingsPhotos> { PhotosScreen(onBack = { it.back() }) }
    composable<SettingsActivities> { ActivitiesScreen(onBack = { it.back() }) }
    composable<SettingsAvailability> { AvailabilityScreen(onBack = { it.back() }) }
    composable<SettingsRadius> { RadiusScreen(onBack = { it.back() }) }
    composable<SettingsNotifications> { NotificationsScreen(onBack = { it.back() }) }
    composable<SettingsPrivacy> { entry -> PrivacySafetyScreen(onBack = { entry.back() }, onNavigate = { route -> entry.ifResumed { navController.navigate(route) } }) }
    composable<SettingsBlockUser> { BlockUserScreen(onBack = { it.back() }) }
    composable<SettingsBlockedUsers> { BlockedUsersScreen(onBack = { it.back() }) }
    composable<SettingsReportUser> { ReportUserScreen(onBack = { it.back() }) }
    composable<SettingsDeleteAccount> { DeleteAccountScreen(onBack = { it.back() }) }
    composable<SettingsExportData> { ExportDataScreen(onBack = { it.back() }) }
}

/**
 * Runs [action] only while this screen is the resumed one, so a double tap during a
 * transition can't pop past the Settings tab or push the same screen twice.
 */
fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action()
}
