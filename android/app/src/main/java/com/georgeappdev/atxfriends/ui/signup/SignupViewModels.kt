package com.georgeappdev.atxfriends.ui.signup

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AppContainer
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.location.LocationPermissionMonitor
import com.georgeappdev.atxfriends.session.PendingSignup
import com.georgeappdev.atxfriends.session.SessionState
import com.google.firebase.auth.FirebaseAuth

/** ViewModel factories for the sign-up screens, wired to the app's repositories. */
object SignupViewModels {

    private val CreationExtras.container: AppContainer get() = (this[APPLICATION_KEY] as AtxFriendsApp).container

    /** Register / "Create an account": no account exists yet. */
    fun emailGate(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container
            LocationGateViewModel("email", c.gateLocation, c.locationPermission.granted, c.locationPermission.locationOn, c.signup::joinWaitlist, outcome = null)
        }
    }

    /** A signed-in account with no `users` doc (new Google account, or resumed after a kill). */
    fun pendingGate(pending: PendingSignup): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container
            val outcome = PendingAccountOutcome(
                pending = {
                    (c.session.state.value as? SessionState.NeedsLocationGate)?.pending?.takeIf { it.uid == pending.uid } ?: pending
                },
                currentUid = { FirebaseAuth.getInstance().currentUser?.uid },
                createUserDoc = c.signup::createUserDocIfMissing,
                abandonAccount = c.signup::abandonPendingAccount,
                resolved = c.session::pendingSignupResolved,
            )
            LocationGateViewModel(pending.path, c.gateLocation, c.locationPermission.granted, c.locationPermission.locationOn, c.signup::joinWaitlist, outcome)
        }
    }

    /** Signed in with an incomplete `users` doc. */
    fun profileSetup(profile: UserProfile): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container
            ProfileSetupViewModel(profile, c.activities, c.photos, c.profiles, onFinished = c.session::retry)
        }
    }

    fun signUp(coordinate: Coordinate): ViewModelProvider.Factory = viewModelFactory {
        initializer { SignUpViewModel(coordinate, container.emailAccounts::create) }
    }

    @Composable
    fun permissionMonitor(): LocationPermissionMonitor =
        (LocalContext.current.applicationContext as AtxFriendsApp).container.locationPermission
}
