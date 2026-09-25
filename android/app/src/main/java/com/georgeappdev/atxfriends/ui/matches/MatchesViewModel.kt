package com.georgeappdev.atxfriends.ui.matches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.SimpaticoState
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.distance.DistanceDisplay
import com.georgeappdev.atxfriends.domain.matching.DecisionWrite
import com.georgeappdev.atxfriends.domain.matching.MatchEntry
import com.georgeappdev.atxfriends.domain.matching.MatchSectioning
import com.georgeappdev.atxfriends.domain.matching.MatchSections
import com.georgeappdev.atxfriends.domain.matching.showUpMeter
import com.georgeappdev.atxfriends.domain.simpatico.SimpaticoScoring
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything a pending card, connected row, or the detail sheet shows for one match. */
data class MatchUi(
    val matchID: String,
    val name: String,
    val photoURLs: List<String>,
    /** Bucketed, e.g. "~5 mi away"; null when hidden (not sharing, or no location). */
    val distanceText: String?,
    val sharedActivities: List<String>,
    /** Category for "You both like {category}"; stored only when no exact activity is shared. */
    val sharedCategory: String?,
    val sharedTimes: List<String>,
    val showUpMeter: String,
    val simpaticoScore: Int?,
    /** I haven't said Yay/Nay yet (iOS MatchDetailView.isPending). */
    val isPending: Boolean,
    val isMutual: Boolean,
    /** The other person's UID (a proposal's receiverID). */
    val otherUserID: String = "",
) {
    val firstPhotoURL: String? get() = photoURLs.firstOrNull()

    /** Connected rows drop " away" from the distance label, as iOS does. */
    val shortDistanceText: String? get() = distanceText?.replace(" away", "")
}

data class MatchesUiState(
    /** First load only — iOS shows "Finding your matches..." until it finishes. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val pending: List<MatchUi> = emptyList(),
    val connected: List<MatchUi> = emptyList(),
    /** The last load failed. Android shows this; iOS sets an error but never displays it. */
    val loadFailed: Boolean = false,
    val selectedMatchID: String? = null,
    /** A Yay/Nay is saving for this match; every decision button is disabled until it finishes. */
    val decidingMatchID: String? = null,
    /** The last Yay/Nay failed to save (Android shows this; iOS sets an error it never displays). */
    val decisionFailed: Boolean = false,
    /** Match whose "You're connected!" celebration is showing. */
    val celebrationMatchID: String? = null,
) {
    val isEmpty: Boolean get() = pending.isEmpty() && connected.isEmpty()
    val selected: MatchUi? get() = selectedMatchID?.let(::find)
    val celebration: MatchUi? get() = celebrationMatchID?.let(::find)
    val isDeciding: Boolean get() = decidingMatchID != null

    private fun find(id: String): MatchUi? = (pending + connected).firstOrNull { it.matchID == id }
}

/**
 * Port of iOS MatchesViewModel. Its only write is a Yay/Nay decision. Unlike iOS, it never
 * creates match documents: iOS runs createPotentialMatches() (which writes new `matches` docs)
 * before every load and refresh; Android only reads the matches that already exist.
 */
class MatchesViewModel(
    private val myID: String,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val fetchMatches: suspend (uid: String) -> List<Match>,
    private val fetchSimpatico: suspend (uid: String) -> SimpaticoState,
    private val recordDecision: suspend (matchID: String, myID: String, yay: Boolean) -> DecisionWrite,
    private val celebrationDurationMillis: Long = CELEBRATION_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(MatchesUiState())
    val state: StateFlow<MatchesUiState> = _state.asStateFlow()

    private var me: UserProfile? = null
    /** Like iOS `matchedUsers`, never cleared: a failed re-fetch keeps the last good profile. */
    private var profiles: Map<String, UserProfile> = emptyMap()
    private var sections = MatchSections(emptyList(), emptyList())
    private var scores: Map<String, Int> = emptyMap()
    private var hasAppeared = false
    private var loadJob: Job? = null

    init {
        loadJob = viewModelScope.launch { load() }
    }

    /** Pull-to-refresh: a full reload (iOS `refresh()`, minus creating new matches). */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(isRefreshing = true) }
        loadJob = viewModelScope.launch {
            load()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Called each time the tab appears. Like iOS `.onAppear`, the first appearance is the
     * initial load; returning later only re-fetches the matched people's profiles.
     */
    fun onAppear() {
        if (!hasAppeared) {
            hasAppeared = true
            return
        }
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            profiles = profiles + fetchProfiles((sections.pending + sections.connected).mapNotNull(::otherID))
            publish()
        }
    }

    fun select(matchID: String) = _state.update { it.copy(selectedMatchID = matchID) }
    fun dismissDetail() = _state.update { it.copy(selectedMatchID = null) }

    /**
     * Yay (true) or Nay (false), from a pending card or the detail sheet (iOS makeDecision).
     * Ignored while another decision is saving. On success: closes the detail sheet if it was
     * for this match, reloads the list like iOS (the card leaves Pending), and celebrates if this
     * Yay completed a mutual match. On failure: shows an error and leaves everything as it was.
     */
    fun decide(matchID: String, yay: Boolean) {
        if (_state.value.isDeciding) return
        _state.update { it.copy(decidingMatchID = matchID, decisionFailed = false) }
        // Replaces any in-flight load, and blocks refreshes until the post-decision reload
        // finishes, so an older read can't put the card back.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val write = attempt { recordDecision(matchID, myID, yay) }
            if (write == null) {
                _state.update { it.copy(decidingMatchID = null, decisionFailed = true, isRefreshing = false) }
                return@launch
            }
            load()
            _state.update {
                it.copy(
                    decidingMatchID = null,
                    isRefreshing = false,
                    selectedMatchID = if (it.selectedMatchID == matchID) null else it.selectedMatchID,
                )
            }
            if (yay && write.completesMutualMatch) celebrate(matchID)
        }
    }

    fun dismissDecisionError() = _state.update { it.copy(decisionFailed = false) }

    /** "Maybe later", or a tap on the dimmed background. */
    fun dismissCelebration() = _state.update { it.copy(celebrationMatchID = null) }

    /** "Open": iOS opens the match detail too (Messages isn't on Android yet). */
    fun openCelebration() = _state.update {
        it.copy(selectedMatchID = it.celebrationMatchID ?: it.selectedMatchID, celebrationMatchID = null)
    }

    private fun celebrate(matchID: String) {
        _state.update { it.copy(celebrationMatchID = matchID) }
        // Auto-dismiss after 6 seconds, like iOS.
        viewModelScope.launch {
            delay(celebrationDurationMillis)
            _state.update { if (it.celebrationMatchID == matchID) it.copy(celebrationMatchID = null) else it }
        }
    }

    private suspend fun load() {
        var failed = false

        // iOS re-fetches the current user on every load (for a fresh blockedUsers list).
        try {
            (fetchUser(myID) as? UserDoc.Found)?.let { me = it.profile }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }

        try {
            val matches = fetchMatches(myID)
            val otherIDs = MatchSectioning.withoutBlocked(matches, myID, me).mapNotNull(::otherID).distinct()
            profiles = profiles + fetchProfiles(otherIDs)
            sections = MatchSectioning.sections(matches, myID, me, profiles)
            scores = fetchScores(sections.connected.mapNotNull(::otherID))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }

        publish(loadFailed = failed)
    }

    private fun publish(loadFailed: Boolean = _state.value.loadFailed) {
        val me = me
        fun ui(entry: MatchEntry) = entry.toUi(me)
        _state.update {
            it.copy(
                isLoading = false,
                pending = MatchSectioning.entries(sections.pending, myID, profiles).map(::ui),
                connected = MatchSectioning.entries(sections.connected, myID, profiles).map(::ui),
                loadFailed = loadFailed,
            )
        }
    }

    private fun MatchEntry.toUi(me: UserProfile?) = MatchUi(
        matchID = match.id,
        otherUserID = other.id,
        name = other.displayName,
        photoURLs = other.photoURLs,
        distanceText = me?.let { DistanceDisplay.between(it, other) },
        sharedActivities = match.overlappingActivityNames,
        sharedCategory = match.overlappingCategoryNames.firstOrNull(),
        sharedTimes = match.overlappingDaySlots,
        showUpMeter = showUpMeter(other.showUpThumbsUp, other.showUpTotal),
        simpaticoScore = scores[other.id],
        isPending = MatchSectioning.isPending(match, myID),
        isMutual = match.isMutualMatch,
    )

    private fun otherID(match: Match): String? = MatchSectioning.otherUserID(match, myID)

    /** Profiles that loaded; a person whose profile fails (or is unreadable) is skipped, as on iOS. */
    private suspend fun fetchProfiles(ids: List<String>): Map<String, UserProfile> = coroutineScope {
        ids.distinct()
            .map { id -> async { id to (attempt { fetchUser(id) } as? UserDoc.Found)?.profile } }
            .awaitAll()
            .mapNotNull { (id, profile) -> profile?.let { id to it } }
            .toMap()
    }

    /** Simpatico % for connected people. A missing or failed fetch means no badge. */
    private suspend fun fetchScores(otherIDs: List<String>): Map<String, Int> = coroutineScope {
        if (otherIDs.isEmpty()) return@coroutineScope emptyMap()
        val mine = attempt { fetchSimpatico(myID) } ?: return@coroutineScope emptyMap()
        otherIDs.distinct()
            .map { id -> async { id to attempt { fetchSimpatico(id) }?.let { SimpaticoScoring.score(mine, it) } } }
            .awaitAll()
            .mapNotNull { (id, score) -> score?.let { id to it } }
            .toMap()
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val CELEBRATION_MILLIS = 6_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AtxFriendsApp).container
                val myID = (container.session.state.value as? SessionState.Ready)?.user?.uid.orEmpty()
                MatchesViewModel(
                    myID = myID,
                    fetchUser = container.users::fetchUser,
                    fetchMatches = container.matches::fetchMatches,
                    fetchSimpatico = container.simpatico::fetchState,
                    recordDecision = container.matches::recordDecision,
                )
            }
        }
    }
}
