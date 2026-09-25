package com.georgeappdev.atxfriends.ui.root

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.navigation.MainScaffold
import com.georgeappdev.atxfriends.session.SessionState
import com.georgeappdev.atxfriends.ui.auth.AuthNavHost
import com.georgeappdev.atxfriends.ui.signup.LocationGateFlow
import com.georgeappdev.atxfriends.ui.signup.ProfileSetupFlow
import com.georgeappdev.atxfriends.ui.signup.SignupViewModels
import kotlin.reflect.KClass

/** Port of iOS RootView: picks the top-level screen from the session state. */
@Composable
fun RootScreen(viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Crossfade by screen kind, so a Ready → Ready profile refresh doesn't rebuild the tabs; each
    // side of a fade renders its own state, so the outgoing screen never goes blank.
    val lastOfKind = remember { mutableMapOf<KClass<out SessionState>, SessionState>() }
    lastOfKind[state::class] = state
    Crossfade(targetState = state::class, label = "root") { kind ->
        when (val s = lastOfKind[kind] ?: state) {
            SessionState.Loading -> LoadingScreen()
            SessionState.SignedOut -> AuthNavHost()
            is SessionState.Ready -> SignedInScope { MainScaffold() }
            // iOS RootView's `pendingNewSSOUser` branch.
            is SessionState.NeedsLocationGate -> key(s.pending.uid) {
                VisitScope {
                    LocationGateFlow(
                        viewModel = viewModel(factory = SignupViewModels.pendingGate(s.pending)),
                        permission = SignupViewModels.permissionMonitor(),
                    )
                }
            }
            // "Authenticated, profile incomplete" → ProfileSetupFlowView.
            is SessionState.NeedsProfileSetup -> key(s.profile.id) {
                VisitScope { ProfileSetupFlow(viewModel(factory = SignupViewModels.profileSetup(s.profile))) }
            }
            is SessionState.NotFinished -> AccountNotFinishedScreen(onSignOut = viewModel::signOut)
            is SessionState.LoadFailed -> LoadFailedScreen(onRetry = viewModel::retry, onSignOut = viewModel::signOut)
        }
    }
}
