package com.georgeappdev.atxfriends.ui.root

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.georgeappdev.atxfriends.navigation.MainScaffold
import com.georgeappdev.atxfriends.session.SessionState
import com.georgeappdev.atxfriends.ui.auth.AuthNavHost

/** Port of iOS RootView: picks the top-level screen from the session state. */
@Composable
fun RootScreen(viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Crossfade by screen kind, so a Ready → Ready profile refresh doesn't rebuild the tabs.
    Crossfade(targetState = state::class, label = "root") { kind ->
        when (kind) {
            SessionState.Loading::class -> LoadingScreen()
            SessionState.SignedOut::class -> AuthNavHost()
            SessionState.Ready::class -> SignedInScope { MainScaffold() }
            SessionState.NotFinished::class -> AccountNotFinishedScreen(onSignOut = viewModel::signOut)
            SessionState.LoadFailed::class -> LoadFailedScreen(onRetry = viewModel::retry, onSignOut = viewModel::signOut)
            else -> LoadingScreen()
        }
    }
}
