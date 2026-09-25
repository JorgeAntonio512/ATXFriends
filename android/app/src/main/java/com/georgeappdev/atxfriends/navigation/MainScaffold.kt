package com.georgeappdev.atxfriends.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.ui.components.AtxTabBar
import com.georgeappdev.atxfriends.ui.messages.UnreadBadgeViewModel

@Composable
fun MainScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val hierarchy = backStackEntry?.destination?.hierarchy
    val selectedTab = AppTab.entries.firstOrNull { tab ->
        hierarchy?.any { it.hasRoute(tab.graph::class) } == true
    } ?: AppTab.MATCHES
    val unreadBadge: UnreadBadgeViewModel = viewModel(factory = UnreadBadgeViewModel.Factory)
    val showsMessagesDot by unreadBadge.showsMessagesDot.collectAsStateWithLifecycle()

    val tabNavigator = remember(navController) { TabNavigator { navController.navigateToTab(it) } }

    // A tapped push (cold or warm start): open that conversation in Messages, like iOS MainTabView.
    val container = (LocalContext.current.applicationContext as AtxFriendsApp).container
    LaunchedEffect(container) {
        container.pushRoutes.pending.collect { route ->
            if (route == null) return@collect
            container.pushRoutes.consume()
            container.threadRequests.request(ThreadRequest(route.matchID, "", "", route.fallbackToMatchesTab))
            navController.navigateToTab(AppTab.MESSAGES)
        }
    }

    CompositionLocalProvider(LocalTabNavigator provides tabNavigator) {
        Scaffold(
            bottomBar = {
                AtxTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { navController.navigateToTab(it) },
                    badgedTabs = if (showsMessagesDot) setOf(AppTab.MESSAGES) else emptySet(),
                )
            },
        ) { innerPadding ->
            AppNavHost(navController, Modifier.padding(innerPadding))
        }
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
