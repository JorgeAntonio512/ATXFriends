package com.georgeappdev.atxfriends.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.georgeappdev.atxfriends.ui.placeholder.PlaceholderScreen
import com.georgeappdev.atxfriends.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MatchesGraph,
        modifier = modifier,
    ) {
        navigation<MatchesGraph>(startDestination = MatchesHome) {
            composable<MatchesHome> { PlaceholderScreen(stringResource(AppTab.MATCHES.label)) }
        }
        navigation<TodayGraph>(startDestination = TodayHome) {
            composable<TodayHome> { PlaceholderScreen(stringResource(AppTab.TODAY.label)) }
        }
        navigation<UpcomingGraph>(startDestination = UpcomingHome) {
            composable<UpcomingHome> { PlaceholderScreen(stringResource(AppTab.UPCOMING.label)) }
        }
        navigation<SimpaticoGraph>(startDestination = SimpaticoHome) {
            composable<SimpaticoHome> { PlaceholderScreen(stringResource(AppTab.SIMPATICO.label)) }
        }
        navigation<MessagesGraph>(startDestination = MessagesHome) {
            composable<MessagesHome> { PlaceholderScreen(stringResource(AppTab.MESSAGES.label)) }
        }
        navigation<SettingsGraph>(startDestination = SettingsHome) {
            composable<SettingsHome> { SettingsScreen() }
        }
    }
}
