package com.georgeappdev.atxfriends.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.session.SessionManager
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.flow.StateFlow

class RootViewModel(private val session: SessionManager) : ViewModel() {
    val state: StateFlow<SessionState> = session.state

    fun retry() = session.retry()
    fun signOut() = session.signOut()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RootViewModel((this[APPLICATION_KEY] as AtxFriendsApp).container.session) }
        }
    }
}
