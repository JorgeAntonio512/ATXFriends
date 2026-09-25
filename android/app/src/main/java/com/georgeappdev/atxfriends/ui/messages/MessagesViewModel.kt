package com.georgeappdev.atxfriends.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AppContainer
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import com.georgeappdev.atxfriends.domain.messages.sortThreadsForDisplay
import com.georgeappdev.atxfriends.navigation.ThreadRequest
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class MessagesUiState(
    /** First load, nothing to show yet: "Loading messages...". */
    val isLoading: Boolean = true,
    /** Pull-to-refresh in progress; the list stays on screen. */
    val isRefreshing: Boolean = false,
    /** The last load failed and there's nothing cached to show. */
    val loadFailed: Boolean = false,
    /** In the order iOS renders them. */
    val threads: List<MessageThread> = emptyList(),
    /** Conversations with unread messages, live — the same source iOS UnreadState uses. */
    val unreadMatchIDs: Set<String> = emptySet(),
    /** The conversation open full screen, if any. */
    val openThread: MessageThread? = null,
    /** A tapped new-match push whose thread isn't in the list: switch to Matches (iOS fallback). */
    val showMatchesTab: Boolean = false,
    val myID: String = "",
    val myPhotoURL: String? = null,
)

/** Port of the thread-list half of iOS MessagingViewModel. */
class MessagesViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * A thread another tab asked to open (Today's "I'm in"), waiting for the next load to finish
     * so a just-created match is in the list (iOS MessagesListView pendingRoute).
     */
    private var pendingThread: ThreadRequest? = null

    init {
        val ready = container.session.state.value as? SessionState.Ready
        if (ready != null) {
            _state.update { it.copy(myID = ready.user.uid, myPhotoURL = ready.profile.photoURLs.firstOrNull()) }
            viewModelScope.launch {
                container.threadRequests.pending.collect { request ->
                    if (request == null) return@collect
                    pendingThread = container.threadRequests.consume()
                    // Already on screen (e.g. a push tapped while Messages is showing): load now.
                    if (!_state.value.isLoading) load(isRefresh = false)
                }
            }
            viewModelScope.launch {
                container.messages.unreadMatchIDs(ready.user.uid)
                    .catch { /* iOS keeps its last unread set when the listener errors. */ }
                    .collect { ids -> _state.update { it.copy(unreadMatchIDs = ids) } }
            }
        }
    }

    /** Each time the tab appears, like iOS (which rebuilds the list view on every tab switch). */
    fun onAppear() = load(isRefresh = false)

    fun refresh() = load(isRefresh = true)

    /**
     * Opens a conversation and, like iOS, marks it read right away, before the thread has even
     * loaded (UnreadState.markConversationRead). A failure here is retried by the open thread.
     */
    fun open(threadID: String) {
        _state.update { s -> s.copy(openThread = s.threads.firstOrNull { it.id == threadID }) }
        val myID = _state.value.myID
        if (myID.isEmpty()) return
        viewModelScope.launch { orNull { container.messages.markConversationRead(threadID, myID) } }
    }

    /**
     * Closes the conversation and refreshes the list so its last message and plan pill are
     * current. (iOS leaves the list as it was until the tab reappears.)
     */
    fun closeThread() {
        _state.update { it.copy(openThread = null) }
        load(isRefresh = false)
    }

    private fun load(isRefresh: Boolean) {
        val myID = _state.value.myID
        if (myID.isEmpty()) {
            _state.update { it.copy(isLoading = false, loadFailed = true) }
            return
        }
        if (loadJob?.isActive == true) return
        _state.update {
            it.copy(
                isLoading = it.threads.isEmpty() && !isRefresh,
                isRefreshing = isRefresh,
                loadFailed = false,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val threads = sortThreadsForDisplay(fetchThreads(myID))
                _state.update { it.copy(threads = threads, isLoading = false, isRefreshing = false) }
                openPendingThread()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadFailed = it.threads.isEmpty())
                }
            }
        }
    }

    /**
     * Opens the requested thread if it loaded, and forgets the request either way, as iOS does
     * (its Matches-tab fallback isn't used for this route).
     */
    private fun openPendingThread() {
        val request = pendingThread ?: return
        pendingThread = null
        when {
            _state.value.threads.any { it.id == request.matchID } -> open(request.matchID)
            request.fallbackToMatchesTab -> _state.update { it.copy(showMatchesTab = true) }
        }
    }

    fun matchesTabShown() = _state.update { it.copy(showMatchesTab = false) }

    /**
     * Same reads as iOS loadMessageThreads: every mutual match, the other person's profile
     * (skipped if missing or unreadable), the last message, and the soonest upcoming plan. The
     * last message and plans are best-effort, as on iOS; failing to fetch matches or a profile
     * fails the whole load, also as on iOS. The per-thread reads run in parallel.
     */
    private suspend fun fetchThreads(myID: String): List<MessageThread> = coroutineScope {
        val now = Instant.now()
        container.matches.fetchMatches(myID)
            .filter { it.isMutualMatch }
            .map { match -> async { fetchThread(match, myID, now) } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun fetchThread(match: Match, myID: String, now: Instant): MessageThread? {
        val otherID = when (myID) {
            match.user1ID -> match.user2ID
            match.user2ID -> match.user1ID
            else -> return null
        }
        val profile = (container.users.fetchUser(otherID) as? UserDoc.Found)?.profile ?: return null
        val lastMessage = orNull { container.messages.fetchLastMessage(match.id) }
        val plans = orNull { container.plans.fetchPlans(match.id) }
        return MessageThread(
            id = match.id,
            match = match,
            otherUserID = otherID,
            otherUserName = profile.displayName,
            otherUserPhotoURL = profile.photoURLs.firstOrNull(),
            lastMessage = lastMessage,
            upcomingPlan = plans?.let { MessageThread.upcomingPlan(it, now) },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MessagesViewModel((this[APPLICATION_KEY] as AtxFriendsApp).container) }
        }
    }
}

/** Swift `try?`: null on failure, but never swallows coroutine cancellation. */
internal suspend fun <T> orNull(block: suspend () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
