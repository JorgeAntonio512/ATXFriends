package com.georgeappdev.atxfriends.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.georgeappdev.atxfriends.ui.components.AtxTabBar

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val hierarchy = backStackEntry?.destination?.hierarchy
    val selectedTab = AppTab.entries.firstOrNull { tab ->
        hierarchy?.any { it.hasRoute(tab.graph::class) } == true
    } ?: AppTab.MATCHES

    Scaffold(
        bottomBar = {
            AtxTabBar(
                selectedTab = selectedTab,
                onTabSelected = { navController.navigateToTab(it) },
            )
        },
    ) { innerPadding ->
        AppNavHost(navController, Modifier.padding(innerPadding))
    }
}

/** Switches tabs, keeping each tab's own back stack (multiple back stacks). */
private fun NavHostController.navigateToTab(tab: AppTab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
