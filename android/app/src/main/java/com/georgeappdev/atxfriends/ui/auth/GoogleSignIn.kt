package com.georgeappdev.atxfriends.ui.auth

import android.content.Context
import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import com.georgeappdev.atxfriends.data.repository.GoogleNames
import com.georgeappdev.atxfriends.data.repository.GoogleSignInResult
import com.georgeappdev.atxfriends.session.PendingSignup
import com.georgeappdev.atxfriends.ui.components.atxText
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ATXF"

/** What the Google account picker returned. */
data class GoogleIdToken(val idToken: String, val displayName: String?)

/**
 * Credential Manager's "Sign in with Google" sheet. Returns null when the person closes it.
 * Needs the app's SHA-1 registered in Firebase (see the android-11 report) or Google refuses.
 */
suspend fun requestGoogleIdToken(activityContext: Context): GoogleIdToken? {
    val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val response = try {
        CredentialManager.create(activityContext).getCredential(activityContext, request)
    } catch (e: GetCredentialCancellationException) {
        Log.i(TAG, "google: picker closed")
        return null
    }
    val credential = response.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type ${credential.type}"
    }
    val google = GoogleIdTokenCredential.createFrom(credential.data)
    return GoogleIdToken(google.idToken, google.displayName)
}

data class GoogleSignInUiState(val isLoading: Boolean = false, val error: AuthMessage? = null)

/**
 * iOS AuthViewModel.handleGoogleSignIn: sign in to Firebase, then branch on `isNewUser`. A new
 * account gets no Firestore doc yet — it goes to the location gate (SessionManager routes there
 * for any signed-in account without a doc). A returning one goes straight in.
 */
class GoogleSignInViewModel(
    private val signInWithGoogle: suspend (idToken: String) -> GoogleSignInResult,
    private val startPendingSignup: (PendingSignup) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(GoogleSignInUiState())
    val state: StateFlow<GoogleSignInUiState> = _state.asStateFlow()

    /** Blocks a second tap while the picker or sign-in is in flight. */
    fun begin(): Boolean {
        if (_state.value.isLoading) return false
        _state.update { it.copy(isLoading = true, error = null) }
        return true
    }

    fun pickerClosed() = _state.update { it.copy(isLoading = false) }

    fun pickerFailed(e: Throwable) {
        Log.w(TAG, "google: credential request failed", e)
        _state.update { it.copy(isLoading = false, error = AuthMessage.GOOGLE_FAILED) }
    }

    fun signIn(token: GoogleIdToken) {
        viewModelScope.launch {
            try {
                // Signing in swaps the screen (and this ViewModel) away; finish regardless.
                withContext(NonCancellable) {
                    val result = signInWithGoogle(token.idToken)
                    Log.i(TAG, "google: signed in, isNewUser=${result.isNewUser}")
                    if (result.isNewUser) {
                        val name = GoogleNames.initialDisplayName(token.displayName, result.firebaseDisplayName)
                        startPendingSignup(PendingSignup(result.uid, name, path = "google"))
                    }
                }
                _state.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "google: Firebase sign-in failed", e)
                _state.update { it.copy(isLoading = false, error = AuthErrorMapper.messageFor(e)) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                GoogleSignInViewModel(c.signup::signInWithGoogle, c.session::startPendingSignup)
            }
        }
    }
}

/** iOS "Continue with Google": white card, black border and text, blue→red "G" mark. */
@Composable
fun GoogleButton(
    viewModel: GoogleSignInViewModel = viewModel(factory = GoogleSignInViewModel.Factory),
    height: Dp = 56.dp,
    fontSize: TextUnit = 18.sp,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color.Black, shape)
                .clickable(enabled = !state.isLoading, role = Role.Button) {
                    if (!viewModel.begin()) return@clickable
                    scope.launch {
                        try {
                            val token = requestGoogleIdToken(context)
                            if (token == null) viewModel.pickerClosed() else viewModel.signIn(token)
                        } catch (e: CancellationException) {
                            viewModel.pickerClosed()
                            throw e
                        } catch (e: Exception) {
                            viewModel.pickerFailed(e)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoogleMark()
                    Text(stringResource(R.string.welcome_continue_google), style = atxText(fontSize, FontWeight.SemiBold), color = Color.Black)
                }
            }
        }
        // iOS only logs a failed Google sign-in; Android shows it.
        state.error?.let { ErrorCard(it) }
    }
}

/** SF Symbol `g.circle.fill` with iOS's googleLogoBlue → googleLogoRed gradient. */
@Composable
private fun GoogleMark() {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0.26f, 0.52f, 0.96f), Color(0.92f, 0.25f, 0.21f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("G", style = atxText(13.sp, FontWeight.Bold), color = Color.White)
    }
}
