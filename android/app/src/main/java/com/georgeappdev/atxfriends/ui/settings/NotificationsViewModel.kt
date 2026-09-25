package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.NotificationPreferences
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.data.repository.UserWrites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The four toggles iOS shows. `groupUpdates` has no toggle (Groups was removed) but is kept. */
enum class NotificationToggle { NEW_MATCHES, MESSAGES, PLAN_REQUESTS, PLAN_CONFIRMATIONS }

data class NotificationsUiState(
    val prefs: NotificationPreferences = NotificationPreferences(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    fun isOn(toggle: NotificationToggle) = when (toggle) {
        NotificationToggle.NEW_MATCHES -> prefs.newMatches
        NotificationToggle.MESSAGES -> prefs.newMessages
        NotificationToggle.PLAN_REQUESTS -> prefs.planRequests
        NotificationToggle.PLAN_CONFIRMATIONS -> prefs.planConfirmations
    }
}

/**
 * iOS NotificationSettingsView's preferences: loaded fresh from `users/{uid}`, and each toggle
 * saved at once with all five keys (iOS `updateNotificationPreferences`). This round only saves
 * the preferences; push delivery comes later.
 */
class NotificationsViewModel(
    private val edits: ProfileEdits,
    private val fetchUser: suspend (String) -> UserDoc,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState(prefs = edits.profile?.notificationPreferences ?: NotificationPreferences()))
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private val saver = AutoSaver<NotificationPreferences>(
        scope = viewModelScope,
        write = { prefs ->
            edits.save({ now -> UserWrites.notificationPreferences(prefs, now) }) { p, now -> p.copy(notificationPreferences = prefs, updatedAt = now) }
        },
        onStatus = { saving, failed -> _state.update { it.copy(isSaving = saving, saveFailed = failed) } },
    )

    init {
        load()
    }

    private fun load() {
        val uid = edits.profile?.id
        if (uid == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val fresh = (fetchUser(uid) as? UserDoc.Found)?.profile?.notificationPreferences
                _state.update { s -> s.copy(prefs = fresh ?: s.prefs, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun setToggle(toggle: NotificationToggle, on: Boolean) {
        val p = _state.value.prefs
        val next = when (toggle) {
            NotificationToggle.NEW_MATCHES -> p.copy(newMatches = on)
            NotificationToggle.MESSAGES -> p.copy(newMessages = on)
            NotificationToggle.PLAN_REQUESTS -> p.copy(planRequests = on)
            NotificationToggle.PLAN_CONFIRMATIONS -> p.copy(planConfirmations = on)
        }
        if (next == p) return
        _state.update { it.copy(prefs = next, saveFailed = false) }
        saver.submit(next)
    }

    fun retrySave() = saver.retry()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                NotificationsViewModel(ProfileEdits(c.session, c.profiles), c.users::fetchUser)
            }
        }
    }
}
