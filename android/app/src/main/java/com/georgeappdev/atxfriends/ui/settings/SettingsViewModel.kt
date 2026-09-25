package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.session.SessionManager
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val email: String? = null,
    /** From `users/{uid}.displayName`. */
    val displayName: String? = null,
)

class SettingsViewModel(private val session: SessionManager) : ViewModel() {

    val state: StateFlow<SettingsUiState> = session.state
        .map { s ->
            if (s is SessionState.Ready) SettingsUiState(s.user.email, s.profile.displayName)
            else SettingsUiState()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun signOut() = session.signOut()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel((this[APPLICATION_KEY] as AtxFriendsApp).container.session) }
        }
    }
}
