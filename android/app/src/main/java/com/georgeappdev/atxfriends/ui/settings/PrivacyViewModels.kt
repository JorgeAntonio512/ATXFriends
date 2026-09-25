package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.PrivacyReader
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.export.DataExport
import com.georgeappdev.atxfriends.domain.export.ExportInput
import com.georgeappdev.atxfriends.domain.export.ExportMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** A person in the block / blocked lists. */
data class PersonRow(val id: String, val name: String, val photoURL: String?)

private fun UserProfile.toRow() = PersonRow(id, displayName, photoURLs.firstOrNull()?.takeIf { it.isNotBlank() })

/** Fetches profiles one by one, skipping any that are missing or fail (as iOS does). */
private suspend fun fetchPeople(ids: Collection<String>, fetchUser: suspend (String) -> UserDoc): List<PersonRow> = coroutineScope {
    ids.map { id ->
        async {
            try {
                (fetchUser(id) as? UserDoc.Found)?.profile?.toRow()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.name }
}

/** How long iOS shows "User Blocked" / "User Unblocked". */
private const val SUCCESS_MS = 1_500L

data class BlockUserUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val pending: List<PersonRow> = emptyList(),
    val connections: List<PersonRow> = emptyList(),
    val isBlocking: Boolean = false,
    val blockFailed: Boolean = false,
    /** Name shown in the "User Blocked" overlay while it's up. */
    val blockedName: String? = null,
    /** iOS leaves the screen after blocking the last person. */
    val done: Boolean = false,
) {
    val isEmpty: Boolean get() = pending.isEmpty() && connections.isEmpty()
}

/**
 * iOS BlockUserView. iOS writes `blockedUsers` (arrayUnion) and then tries to set
 * `isBlocked`/`isMutualMatch` on the match docs — a write the matches rules reject, so on iOS
 * the block lands but the screen reports failure. Android writes only `blockedUsers`, which is
 * what every reader (Matches, Messages, the Cloud Functions) checks.
 */
class BlockUserViewModel(
    private val edits: ProfileEdits,
    private val privacy: PrivacyReader,
    private val fetchUser: suspend (String) -> UserDoc,
) : ViewModel() {

    private val _state = MutableStateFlow(BlockUserUiState())
    val state: StateFlow<BlockUserUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val me = edits.profile ?: return
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                // Fresh blockedUsers, like iOS loadCurrentUser.
                val blocked = ((fetchUser(me.id) as? UserDoc.Found)?.profile ?: me).blockedUsers.toSet()
                val candidates = privacy.blockCandidates(me.id, blocked)
                val pending = fetchPeople(candidates.pendingIDs, fetchUser)
                val connections = fetchPeople(candidates.mutualIDs, fetchUser)
                _state.update { it.copy(isLoading = false, pending = pending, connections = connections) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun block(person: PersonRow) {
        if (_state.value.isBlocking) return
        _state.update { it.copy(isBlocking = true, blockFailed = false) }
        viewModelScope.launch {
            try {
                edits.save({ UserWrites.block(person.id) }) { p, _ ->
                    if (person.id in p.blockedUsers) p else p.copy(blockedUsers = p.blockedUsers + person.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isBlocking = false, blockFailed = true) }
                return@launch
            }
            _state.update { s ->
                s.copy(
                    isBlocking = false,
                    blockedName = person.name,
                    pending = s.pending.filterNot { it.id == person.id },
                    connections = s.connections.filterNot { it.id == person.id },
                )
            }
            delay(SUCCESS_MS)
            _state.update { it.copy(blockedName = null) }
            if (_state.value.isEmpty) {
                delay(500)
                _state.update { it.copy(done = true) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(blockFailed = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                BlockUserViewModel(ProfileEdits(c.session, c.profiles), c.privacy, c.users::fetchUser)
            }
        }
    }
}

data class BlockedUsersUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val blocked: List<PersonRow> = emptyList(),
    val isUnblocking: Boolean = false,
    val unblockFailed: Boolean = false,
    val unblockedName: String? = null,
)

/**
 * iOS BlockedUsersView: the people in `blockedUsers`. Unblocking removes them with arrayRemove
 * (iOS also tries to reset `isBlocked` on match docs, which the rules reject — see above).
 */
class BlockedUsersViewModel(
    private val edits: ProfileEdits,
    private val fetchUser: suspend (String) -> UserDoc,
) : ViewModel() {

    private val _state = MutableStateFlow(BlockedUsersUiState())
    val state: StateFlow<BlockedUsersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val me = edits.profile ?: return
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val ids = ((fetchUser(me.id) as? UserDoc.Found)?.profile ?: me).blockedUsers
                val people = fetchPeople(ids, fetchUser)
                _state.update { it.copy(isLoading = false, blocked = people) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun unblock(person: PersonRow) {
        if (_state.value.isUnblocking) return
        _state.update { it.copy(isUnblocking = true, unblockFailed = false) }
        viewModelScope.launch {
            try {
                edits.save({ UserWrites.unblock(person.id) }) { p, _ -> p.copy(blockedUsers = p.blockedUsers - person.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isUnblocking = false, unblockFailed = true) }
                return@launch
            }
            _state.update { s -> s.copy(isUnblocking = false, unblockedName = person.name, blocked = s.blocked.filterNot { it.id == person.id }) }
            delay(SUCCESS_MS)
            _state.update { it.copy(unblockedName = null) }
        }
    }

    fun dismissError() = _state.update { it.copy(unblockFailed = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                BlockedUsersViewModel(ProfileEdits(c.session, c.profiles), c.users::fetchUser)
            }
        }
    }
}

data class ExportUiState(
    val isExporting: Boolean = false,
    val failed: Boolean = false,
    /** The finished export, waiting for the screen to hand it to the share sheet. */
    val readyText: String? = null,
)

/**
 * iOS ExportDataView + exportUserData: builds the plain-text export on the device from the
 * profile, mutual matches, messages and plans (no writes), then opens the share sheet. A
 * section that fails to load prints "Error loading …" instead of failing the whole export, as
 * on iOS; only an unreadable profile fails it.
 */
class ExportDataViewModel(
    private val currentUid: () -> String?,
    private val privacy: PrivacyReader,
    private val fetchUser: suspend (String) -> UserDoc,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun export() {
        if (_state.value.isExporting) return
        val uid = currentUid() ?: return
        _state.update { it.copy(isExporting = true, failed = false, readyText = null) }
        viewModelScope.launch {
            val text = try {
                build(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { it.copy(isExporting = false, failed = text == null, readyText = text) }
        }
    }

    fun shareHandled() = _state.update { it.copy(readyText = null) }
    fun shareFailed() = _state.update { it.copy(readyText = null, failed = true) }
    fun dismissError() = _state.update { it.copy(failed = false) }

    private suspend fun build(uid: String): String? {
        val profile = (fetchUser(uid) as? UserDoc.Found)?.profile ?: return null
        val names = mutableMapOf<String, String?>()
        suspend fun nameOf(id: String): String? = names.getOrPut(id) {
            try {
                (fetchUser(id) as? UserDoc.Found)?.profile?.displayName
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        val matches = section { privacy.exportMatches(uid).map { ExportMatch(nameOf(it.otherUserID), it.createdAt) } }
        val messages = section { privacy.exportMessages(uid) }
        messages?.forEach { m -> nameOf(if (m.senderID == uid) m.receiverID else m.senderID) }
        val plans = section { privacy.exportPlans(uid) }

        val input = ExportInput(
            uid = uid,
            profile = profile,
            matches = matches,
            messages = messages,
            plans = plans,
            names = names.filterValues { it != null }.mapValues { it.value!! },
        )
        return DataExport.build(input, clock(), zone())
    }

    private suspend fun <T> section(load: suspend () -> T): T? = try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                ExportDataViewModel({ c.session.currentProfile?.id }, c.privacy, c.users::fetchUser)
            }
        }
    }
}
