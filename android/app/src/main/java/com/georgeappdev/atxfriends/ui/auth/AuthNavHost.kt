package com.georgeappdev.atxfriends.ui.auth

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.ui.signup.LocationGateFlow
import com.georgeappdev.atxfriends.ui.signup.SignUpScreen
import com.georgeappdev.atxfriends.ui.signup.SignupViewModels
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute
@Serializable data object SignInRoute
@Serializable data object EmailGateRoute
@Serializable data class SignUpRoute(val latitude: Double, val longitude: Double)

/**
 * Signed-out flow. Welcome → Sign In, or Welcome (or Sign In's "Create an account") → location
 * gate → Sign Up. The email path creates no account until the gate has passed.
 */
@Composable
fun AuthNavHost() {
    val navController = rememberNavController()
    val toGate = { navController.navigate(EmailGateRoute) { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                onRegister = toGate,
                onSignIn = { navController.navigate(SignInRoute) { launchSingleTop = true } },
            )
        }
        composable<SignInRoute> {
            SignInScreen(onBack = { navController.popBackStack() }, onCreateAccount = toGate)
        }
        composable<EmailGateRoute> {
            LocationGateFlow(
                viewModel = viewModel(factory = SignupViewModels.emailGate()),
                permission = SignupViewModels.permissionMonitor(),
                onPassed = { c -> navController.navigate(SignUpRoute(c.latitude, c.longitude)) { launchSingleTop = true } },
                onDismissed = { navController.popBackStack() },
            )
        }
        composable<SignUpRoute> { entry ->
            val route = entry.toRoute<SignUpRoute>()
            SignUpScreen(
                viewModel = viewModel(factory = SignupViewModels.signUp(Coordinate(route.latitude, route.longitude))),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
