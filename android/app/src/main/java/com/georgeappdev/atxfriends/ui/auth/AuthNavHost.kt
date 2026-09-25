package com.georgeappdev.atxfriends.ui.auth

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute
@Serializable data object SignInRoute

/** Signed-out flow: Welcome → Sign In. */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            WelcomeScreen(onSignIn = { navController.navigate(SignInRoute) { launchSingleTop = true } })
        }
        composable<SignInRoute> {
            SignInScreen(onBack = { navController.popBackStack() })
        }
    }
}
