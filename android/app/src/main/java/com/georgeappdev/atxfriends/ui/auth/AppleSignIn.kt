package com.georgeappdev.atxfriends.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.auth.AuthErrorMapper
import com.georgeappdev.atxfriends.data.auth.AuthMessage
import com.georgeappdev.atxfriends.data.repository.AppleSignInResult
import com.georgeappdev.atxfriends.session.PendingSignup
import com.georgeappdev.atxfriends.ui.components.atxText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ATXF"

/**
 * iOS AuthViewModel.handleAppleSignIn, after Firebase has signed in: a new account gets no
 * Firestore doc yet — it waits at the location gate with Apple's one-time name (SessionManager
 * routes any signed-in account without a doc there). A returning one goes straight in.
 */
class AppleSignInHandler(
    private val startPendingSignup: (PendingSignup) -> Unit,
    /** AppleAuthRepository.saveNameIfMissing — best effort. */
    private val saveName: suspend (String) -> Unit,
) {
    suspend fun signedIn(result: AppleSignInResult) {
        if (!result.isNewUser) return
        if (result.displayName.isNotEmpty()) {
            try {
                saveName(result.displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The in-memory pending sign-up below still carries the name.
                Log.w(TAG, "apple: couldn't save the name on the auth account", e)
            }
        }
        startPendingSignup(PendingSignup(result.uid, result.displayName, path = "apple"))
    }
}

data class AppleSignInUiState(val isLoading: Boolean = false, val error: AuthMessage? = null)

/** The Sign in with Apple button's state. The web sign-in itself is passed in by the button. */
class AppleSignInViewModel(private val handler: AppleSignInHandler) : ViewModel() {
    private val _state = MutableStateFlow(AppleSignInUiState())
    val state: StateFlow<AppleSignInUiState> = _state.asStateFlow()

    /** [start] opens Apple's page and returns null if the person closed it. */
    fun signIn(start: suspend () -> AppleSignInResult?) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                // Signing in swaps the screen (and this ViewModel) away; finish regardless, or a
                // new account would lose its one-time name and `isNewUser`.
                withContext(NonCancellable) {
                    start()?.let { handler.signedIn(it) }
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "apple: sign-in failed", e)
                _state.update { it.copy(isLoading = false, error = messageFor(e)) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        /** Connection and disabled-account problems keep iOS's wording; anything else is Apple's. */
        fun messageFor(e: Throwable): AuthMessage = when (val m = AuthErrorMapper.messageFor(e)) {
            AuthMessage.NETWORK, AuthMessage.TOO_MANY_REQUESTS, AuthMessage.USER_DISABLED -> m
            else -> AuthMessage.APPLE_FAILED
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppleSignInViewModel((this[APPLICATION_KEY] as AtxFriendsApp).container.appleSignIn)
            }
        }
    }
}

/**
 * iOS's custom "Sign in with Apple" button: white, 1dp black border, black Apple logo and text
 * — Apple's "white with outline" style, which Apple's guidelines allow on any background.
 */
@Composable
fun AppleButton(
    viewModel: AppleSignInViewModel = viewModel(factory = AppleSignInViewModel.Factory),
    height: Dp = 56.dp,
    fontSize: TextUnit = 18.sp,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val apple = (LocalContext.current.applicationContext as AtxFriendsApp).container.appleAuth
    val shape = RoundedCornerShape(16.dp)
    val label = stringResource(R.string.welcome_sign_in_apple)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color.Black, shape)
                .clickable(enabled = !state.isLoading && activity != null, role = Role.Button) {
                    activity?.let { a -> viewModel.signIn { apple.signIn(a) } }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_apple_logo), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Text(label, style = atxText(fontSize, FontWeight.SemiBold), color = Color.Black)
                }
            }
        }
        // iOS only logs a failed Apple sign-in; Android shows it, as it does for Google.
        state.error?.let { ErrorCard(it) }
    }
}

/**
 * Delete Account's "confirm with Apple" step: re-authenticates through Apple's page for a fresh
 * access token. Returns null when the person closes the page (or there's no activity).
 */
fun interface AppleReauth {
    suspend fun freshAccessToken(): String?
}

@Composable
fun rememberAppleReauth(): AppleReauth {
    val activity: Activity? = LocalActivity.current
    val apple = (LocalContext.current.applicationContext as AtxFriendsApp).container.appleAuth
    return remember(activity, apple) { AppleReauth { activity?.let { apple.freshAccessToken(it) } } }
}
