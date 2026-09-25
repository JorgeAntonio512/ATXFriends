package com.georgeappdev.atxfriends.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.repository.UnreadMessages
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Messages tab's red dot (iOS UnreadState.hasUnreadMessages): shown while any conversation
 * has a message to the signed-in user that's still unread. Follows the signed-in user, so it
 * clears on sign-out and restarts for whoever signs in next.
 */
class UnreadBadgeViewModel(
    signedInUid: Flow<String?>,
    unreadMatchIDs: (uid: String) -> Flow<Set<String>>,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val showsMessagesDot: StateFlow<Boolean> = signedInUid
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(false)
            else unreadMatchIDs(uid)
                .map(UnreadMessages::showsTabDot)
                // iOS keeps its last state when the listener errors; the dot just stops updating.
                .catch { }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AtxFriendsApp).container
                UnreadBadgeViewModel(
                    signedInUid = container.session.state.map { (it as? SessionState.Ready)?.user?.uid },
                    unreadMatchIDs = container.messages::unreadMatchIDs,
                )
            }
        }
    }
}
