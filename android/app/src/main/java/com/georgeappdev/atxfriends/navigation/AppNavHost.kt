package com.georgeappdev.atxfriends.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.georgeappdev.atxfriends.ui.matches.MatchesScreen
import com.georgeappdev.atxfriends.ui.messages.MessagesScreen
import com.georgeappdev.atxfriends.ui.settings.SettingsScreen
import com.georgeappdev.atxfriends.ui.settings.ifResumed
import com.georgeappdev.atxfriends.ui.settings.settingsDestinations
import com.georgeappdev.atxfriends.ui.simpatico.SimpaticoScreen
import com.georgeappdev.atxfriends.ui.today.TodayScreen
import com.georgeappdev.atxfriends.ui.upcoming.UpcomingScreen

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
            composable<MatchesHome> { MatchesScreen() }
        }
        navigation<TodayGraph>(startDestination = TodayHome) {
            composable<TodayHome> { TodayScreen() }
        }
        navigation<UpcomingGraph>(startDestination = UpcomingHome) {
            composable<UpcomingHome> { UpcomingScreen() }
        }
        navigation<SimpaticoGraph>(startDestination = SimpaticoHome) {
            composable<SimpaticoHome> { SimpaticoScreen() }
        }
        navigation<MessagesGraph>(startDestination = MessagesHome) {
            composable<MessagesHome> { MessagesScreen() }
        }
        navigation<SettingsGraph>(startDestination = SettingsHome) {
            composable<SettingsHome> { entry -> SettingsScreen(onNavigate = { route -> entry.ifResumed { navController.navigate(route) } }) }
            settingsDestinations(navController)
        }
    }
}
