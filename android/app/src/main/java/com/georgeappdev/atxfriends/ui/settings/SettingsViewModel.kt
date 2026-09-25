package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.location.LocationSharing
import com.georgeappdev.atxfriends.location.LocationSource
import com.georgeappdev.atxfriends.session.SessionManager
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Share My Location" card state (iOS ShareMyLocationContent). */
data class ShareLocationUi(
    /** What the segmented picker shows; follows the saved mode unless a change is in flight. */
    val selected: LocationSharingMode = LocationSharingMode.OFF,
    val isUpdating: Boolean = false,
    /** Set when the screen should ask for location permission for this mode. */
    val permissionRequestFor: LocationSharingMode? = null,
    val showPermissionDenied: Boolean = false,
    val saveFailed: Boolean = false,
    /** The mode just saved here, shown until the refreshed profile catches up. */
    val confirmed: LocationSharingMode? = null,
)

data class SettingsUiState(
    val profile: UserProfile? = null,
    val share: ShareLocationUi = ShareLocationUi(),
) {
    val radiusMiles: Int get() = profile?.radiusMiles?.toInt() ?: 10
}

class SettingsViewModel(
    private val session: SessionManager,
    private val fetchUser: suspend (String) -> UserDoc,
    private val sharing: LocationSharing,
    private val location: LocationSource,
) : ViewModel() {

    private val share = MutableStateFlow(ShareLocationUi())

    val state: StateFlow<SettingsUiState> = combine(session.state, share) { s, sh ->
        val profile = (s as? SessionState.Ready)?.profile
        val inFlight = sh.isUpdating || sh.permissionRequestFor != null
        val selected = if (inFlight) sh.selected else sh.confirmed ?: savedMode(profile)
        SettingsUiState(profile, sh.copy(selected = selected))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private val uid: String? get() = session.currentProfile?.id

    /** iOS `.task { loadUserProfile() }`: refresh from Firestore each time Settings appears. */
    fun onAppear() {
        val id = uid ?: return
        share.update { it.copy(confirmed = null) }
        viewModelScope.launch {
            try {
                (fetchUser(id) as? UserDoc.Found)?.let { session.profileChanged(it.profile) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep showing the profile already loaded; every screen still works from it.
            }
        }
    }

    fun signOut() = session.signOut()

    // Share My Location — ShareMyLocationContent.handleModeChange.

    fun selectShareMode(mode: LocationSharingMode) {
        val current = state.value.share
        if (current.isUpdating || current.permissionRequestFor != null || mode == current.selected) return
        when {
            mode == LocationSharingMode.OFF -> save(mode, withFix = false)
            location.hasPermission() -> save(mode, withFix = true)
            else -> share.update { it.copy(selected = mode, permissionRequestFor = mode, saveFailed = false) }
        }
    }

    /** The screen's permission prompt finished. */
    fun onPermissionResult(granted: Boolean) {
        val mode = share.value.permissionRequestFor ?: return
        share.update { it.copy(permissionRequestFor = null) }
        if (granted) save(mode, withFix = true) else revertToOffWithPermissionMessage()
    }

    fun updateNow() {
        val id = uid ?: return
        if (share.value.isUpdating) return
        if (!location.hasPermission()) {
            revertToOffWithPermissionMessage()
            return
        }
        share.update { it.copy(selected = LocationSharingMode.ONCE, isUpdating = true, saveFailed = false) }
        viewModelScope.launch {
            val ok = attempt { sharing.updateNow(id) }
            share.update { it.copy(isUpdating = false, saveFailed = !ok, confirmed = if (ok) LocationSharingMode.ONCE else it.confirmed) }
        }
    }

    fun dismissPermissionDenied() = share.update { it.copy(showPermissionDenied = false) }
    fun dismissShareError() = share.update { it.copy(saveFailed = false) }

    private fun revertToOffWithPermissionMessage() {
        share.update { it.copy(showPermissionDenied = true) }
        save(LocationSharingMode.OFF, withFix = false)
    }

    private fun save(mode: LocationSharingMode, withFix: Boolean) {
        val id = uid ?: return
        share.update { it.copy(selected = mode, isUpdating = true, saveFailed = false) }
        viewModelScope.launch {
            val ok = attempt { sharing.setMode(id, mode, withFix) }
            // On failure the picker falls back to the saved mode, with a visible error.
            share.update { it.copy(isUpdating = false, saveFailed = !ok, confirmed = if (ok) mode else it.confirmed) }
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        fun savedMode(profile: UserProfile?): LocationSharingMode =
            profile?.locationSharingMode?.takeIf { it != LocationSharingMode.UNKNOWN } ?: LocationSharingMode.OFF

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                SettingsViewModel(c.session, c.users::fetchUser, c.locationSharing, c.deviceLocation)
            }
        }
    }
}
