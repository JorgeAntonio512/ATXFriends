package com.georgeappdev.atxfriends.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.Activity
import com.georgeappdev.atxfriends.data.model.Match
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.TodayPlanStatus
import com.georgeappdev.atxfriends.data.repository.PlanPlace
import com.georgeappdev.atxfriends.data.repository.PlanWrites
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.plans.ComposerTimes
import com.georgeappdev.atxfriends.domain.plans.DayChoice
import com.georgeappdev.atxfriends.domain.plans.PlaceSearch
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchState
import com.georgeappdev.atxfriends.domain.plans.PlaceSuggestion
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** iOS `ProposePlanSheet.Mode`. */
sealed interface ComposerMode {
    /** From a connected match or a thread: writes a pending 1-on-1 Plan + a planProposal message. */
    data class Proposal(val matchID: String, val receiverID: String) : ComposerMode

    /** Today's open post: writes a TodayPlan anyone can claim. */
    data object OpenPost : ComposerMode

    /** Upcoming's invite: writes a GroupPlan to the checked mutual matches. */
    data object GroupInvite : ComposerMode
}

/** An open-slot ghost card's What/When (iOS OpenPostPrefill / GroupInvitePrefill). */
data class ComposerPrefill(val activityName: String, val date: Instant)

/**
 * One request to open the composer. [id] is unique per opening, so each opening gets fresh
 * state. [initialActivity] seeds `.proposal`'s activity (a thread's shared-interest chip).
 */
data class ComposerRequest(
    val mode: ComposerMode,
    val prefill: ComposerPrefill? = null,
    val initialActivity: String? = null,
    val id: String = UUID.randomUUID().toString(),
)

/** What a successful submit wrote; the host screen reacts (e.g. Today's "Posted!" toast). */
sealed interface ComposerResult {
    data class Proposed(val matchID: String, val planID: String) : ComposerResult
    /** The plan as posted, for Today's optimistic insert. */
    data class Posted(val plan: TodayPlan) : ComposerResult
    data class Invited(val planID: String) : ComposerResult
}

/** A mutual match the host can invite (iOS MutualMatchOption). */
data class InviteeOption(
    val userID: String,
    val displayName: String,
    val photoURL: String?,
    /** Lower-cased, for pre-checking people who share a prefilled activity. */
    val activityNames: List<String>,
)

/** The "Where?" field's values (iOS PlanLocationField bindings). */
data class WhereState(
    val text: String = "",
    /** Set only by picking a place-search result; cleared as soon as the text is edited. */
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Focused and then left at least once — gates the missing-location hint. */
    val touched: Boolean = false,
) {
    val isMissing: Boolean get() = text.isBlank()

    /** The red hint shows only after the field was touched and left empty; it clears on typing. */
    val showMissingHint: Boolean get() = touched && isMissing
}

data class ComposerUiState(
    val mode: ComposerMode,
    val activityName: String = "",
    /** Suggestion chips (at most 8); empty hides the row. */
    val suggestions: List<String> = emptyList(),
    val isPrefilled: Boolean = false,
    val dayChoice: DayChoice = DayChoice.TODAY,
    val todayAvailable: Boolean = true,
    val date: Instant,
    val zone: ZoneId,
    val where: WhereState = WhereState(),
    /** Place-search suggestions for the "Where?" text (always Idle with no Places API key). */
    val placeSearch: PlaceSearchState = PlaceSearchState.Idle,
    val inviteeOptions: List<InviteeOption> = emptyList(),
    val isLoadingInvitees: Boolean = false,
    /** Android-only: iOS shows "no mutual matches" when this load fails. */
    val inviteesLoadFailed: Boolean = false,
    val selectedInviteeIDs: Set<String> = emptySet(),
    val isSubmitting: Boolean = false,
    /**
     * The write has been waiting a while — almost always no connection. Firestore has queued it
     * and will send it when the phone is back online, so the sheet says so and can be closed.
     */
    val waitingForConnection: Boolean = false,
    val errorMessage: String? = null,
    /** Non-null once the write succeeded: the sheet dismisses itself. */
    val result: ComposerResult? = null,
) {
    /** iOS `canSubmit`: an activity, a place, not already saving, and (invites) someone checked. */
    val canSubmit: Boolean
        get() = activityName.isNotBlank() && !where.isMissing && !isSubmitting &&
            (mode !is ComposerMode.GroupInvite || selectedInviteeIDs.isNotEmpty())
}

/**
 * Port of the logic in iOS `ProposePlanSheet`: one composer, three modes. Everything is shared
 * unless a mode changes it — the header text, the When control, the activity suggestion source,
 * the invitee checklist, and the write.
 */
/** See [ComposerUiState.waitingForConnection]. */
const val SLOW_WRITE_AFTER_MS = 10_000L

class PlanComposerViewModel(
    private val request: ComposerRequest,
    private val myID: String,
    private val fetchActivityNames: suspend () -> List<String>,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val fetchMatches: suspend (uid: String) -> List<Match>,
    private val propose: suspend (plan: NewDocument, message: (planID: String) -> NewDocument) -> String,
    private val postTodayPlan: suspend (NewDocument) -> String,
    private val createGroupPlan: suspend (NewDocument) -> String,
    private val placeSearch: PlaceSearch = PlaceSearch.None,
    /** How long a write may take before the sheet says it's waiting for a connection. */
    private val slowWriteAfterMs: Long = SLOW_WRITE_AFTER_MS,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val mode = request.mode
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()

    /** Suggestion-chip source: the shared list, or (open posts) the user's own profile activities. */
    private var availableActivities: List<String> = emptyList()
    private var inviteesJob: Job? = null
    private var searchJob: Job? = null
    private var resolveJob: Job? = null

    /** My profile, fetched once: open-post chips and the place-search bias both use it. */
    private val myProfile = viewModelScope.async(start = CoroutineStart.LAZY) {
        (attempt { fetchUser(myID) } as? UserDoc.Found)?.profile
    }

    init {
        viewModelScope.launch { loadActivities() }
        if (mode is ComposerMode.GroupInvite) loadInvitees()
    }

    private fun initialState(): ComposerUiState {
        val now = clock()
        val zone = zone()
        val prefill = request.prefill
        val base = ComposerUiState(mode = mode, date = now, zone = zone, todayAvailable = ComposerTimes.todayIsAvailable(now, zone))
        return when (mode) {
            is ComposerMode.Proposal -> base.copy(
                activityName = request.initialActivity.orEmpty(),
                date = ComposerTimes.nextHourRoundedUp(now, zone),
            )
            ComposerMode.OpenPost -> if (prefill != null) {
                val isToday = prefill.date.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
                base.copy(
                    activityName = prefill.activityName,
                    dayChoice = if (isToday) DayChoice.TODAY else DayChoice.TOMORROW,
                    date = prefill.date,
                    isPrefilled = true,
                )
            } else {
                val choice = ComposerTimes.defaultDayChoice(now, zone)
                base.copy(dayChoice = choice, date = ComposerTimes.defaultPickerTime(choice, now, zone))
            }
            ComposerMode.GroupInvite -> if (prefill != null) {
                base.copy(activityName = prefill.activityName, date = prefill.date, isPrefilled = true)
            } else {
                base.copy(date = ComposerTimes.nextHourRoundedUp(now, zone))
            }
        }
    }

    // ── Activity ───────────────────────────────────────────────────────────────────────

    fun onActivityChange(text: String) {
        _state.update { it.copy(activityName = text, suggestions = filter(text)) }
    }

    /** Tapping a chip fills the field and hides the chips. */
    fun onSuggestionPicked(name: String) {
        _state.update { it.copy(activityName = name, suggestions = emptyList()) }
    }

    /** iOS `filterActivities`: case-insensitive "contains", first 8, hidden for a blank query. */
    private fun filter(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return availableActivities.filter { it.contains(trimmed, ignoreCase = true) }.take(MAX_SUGGESTIONS)
    }

    /** A failed load just means no chips; typing any activity still works (as on iOS). */
    private suspend fun loadActivities() {
        availableActivities = attempt {
            when (mode) {
                is ComposerMode.Proposal, ComposerMode.GroupInvite -> fetchActivityNames()
                ComposerMode.OpenPost -> myProfile.await()?.activities?.map { it.name }
            }
        }.orEmpty()
    }

    // ── When ───────────────────────────────────────────────────────────────────────────

    /** `.openPost`: switching segments resets the time to that segment's default. */
    fun onDayChoice(choice: DayChoice) {
        val now = clock()
        val zone = zone()
        if (choice == DayChoice.TODAY && !ComposerTimes.todayIsAvailable(now, zone)) return
        _state.update {
            it.copy(dayChoice = choice, date = ComposerTimes.defaultPickerTime(choice, now, zone), todayAvailable = ComposerTimes.todayIsAvailable(now, zone), zone = zone)
        }
    }

    /** `.openPost` time picker. */
    fun onPostTimePicked(time: LocalTime) {
        val now = clock()
        val zone = zone()
        _state.update {
            it.copy(date = ComposerTimes.openPostTime(it.dayChoice, time, now, zone), todayAvailable = ComposerTimes.todayIsAvailable(now, zone), zone = zone)
        }
    }

    /** `.proposal` / `.groupInvite` calendar day. Keeps the chosen clock time. */
    fun onDatePicked(date: LocalDate) {
        _state.update {
            val time = it.date.atZone(it.zone).toLocalTime()
            it.copy(date = ComposerTimes.anyFutureTime(date, time, clock(), it.zone))
        }
    }

    /** `.proposal` / `.groupInvite` time. Keeps the chosen day. */
    fun onTimePicked(time: LocalTime) {
        _state.update {
            val day = it.date.atZone(it.zone).toLocalDate()
            it.copy(date = ComposerTimes.anyFutureTime(day, time, clock(), it.zone))
        }
    }

    // ── Where ──────────────────────────────────────────────────────────────────────────

    /** Editing the text drops a picked place's name and coordinates unless it still matches. */
    fun onWhereChange(text: String) {
        if (text != _state.value.where.text) resolveJob?.cancel()
        _state.update {
            val w = it.where
            it.copy(where = if (text != w.locationName) w.copy(text = text, locationName = null, latitude = null, longitude = null) else w.copy(text = text))
        }
        search(text)
    }

    fun onWhereCleared() {
        resolveJob?.cancel()
        _state.update { it.copy(where = it.where.copy(text = "", locationName = null, latitude = null, longitude = null)) }
        search("")
    }

    /**
     * iOS feeds every keystroke to the completer (`queryFragment`); the newest query wins. Search
     * is biased to my stored signup coordinates, never a fresh location fix.
     */
    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val me = if (placeSearch === PlaceSearch.None || query.isBlank()) null else myProfile.await()
            placeSearch.search(query, me?.latitude, me?.longitude).collect { s -> _state.update { it.copy(placeSearch = s) } }
        }
    }

    /** The field lost focus after having it. */
    fun onWhereLeft() {
        _state.update { it.copy(where = it.where.copy(touched = true)) }
    }

    /**
     * A place-search pick fills the text right away. The name and coordinates are saved together
     * once the coordinates arrive (iOS never saves one without the other); if that lookup fails,
     * or the text is edited first, the pick stays plain free text.
     */
    fun onPlacePicked(place: PlaceSuggestion) {
        searchJob?.cancel()
        resolveJob?.cancel()
        _state.update {
            it.copy(where = it.where.copy(text = place.name, locationName = null, latitude = null, longitude = null), placeSearch = PlaceSearchState.Idle)
        }
        resolveJob = viewModelScope.launch {
            val spot = attempt { placeSearch.resolve(place) } ?: return@launch
            _state.update {
                if (it.where.text != place.name) it
                else it.copy(where = it.where.copy(locationName = place.name, latitude = spot.latitude, longitude = spot.longitude))
            }
        }
    }

    override fun onCleared() {
        placeSearch.endSession()
    }

    // ── Invitees (.groupInvite) ────────────────────────────────────────────────────────

    fun onToggleInvitee(userID: String) {
        _state.update {
            val selected = it.selectedInviteeIDs
            it.copy(selectedInviteeIDs = if (userID in selected) selected - userID else selected + userID)
        }
    }

    fun retryInvitees() = loadInvitees()

    /**
     * Every mutual match, with the other person's name, first photo and activities. If opened
     * from a ghost card, people who share that activity start checked (they can be unchecked).
     */
    private fun loadInvitees() {
        if (inviteesJob?.isActive == true) return
        inviteesJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingInvitees = true, inviteesLoadFailed = false) }
            val matches = attempt { fetchMatches(myID) }
            if (matches == null) {
                _state.update { it.copy(isLoadingInvitees = false, inviteesLoadFailed = true) }
                return@launch
            }
            val options = coroutineScope {
                matches.filter { it.isMutualMatch }
                    .mapNotNull { m -> otherUserID(m) }
                    .distinct()
                    .map { id -> async { (attempt { fetchUser(id) } as? UserDoc.Found)?.profile } }
                    .awaitAll()
                    .filterNotNull()
                    .map { p -> InviteeOption(p.id, p.displayName, p.photoURLs.firstOrNull(), p.activities.map { it.name.lowercase() }) }
            }
            val prefillActivity = if (mode is ComposerMode.GroupInvite) request.prefill?.activityName?.lowercase() else null
            _state.update { s ->
                s.copy(
                    inviteeOptions = options,
                    isLoadingInvitees = false,
                    selectedInviteeIDs = if (prefillActivity != null) {
                        options.filter { prefillActivity in it.activityNames }.mapTo(mutableSetOf()) { it.userID }
                    } else {
                        s.selectedInviteeIDs
                    },
                )
            }
        }
    }

    private fun otherUserID(match: Match): String? = when (myID) {
        match.user1ID -> match.user2ID
        match.user2ID -> match.user1ID
        else -> null
    }

    // ── Submit ─────────────────────────────────────────────────────────────────────────

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    /** Writes the plan for this mode. Ignored while a save is running; input is kept on failure. */
    fun submit() {
        val s = _state.value
        if (!s.canSubmit || myID.isEmpty()) return
        val name = s.activityName.trim()
        _state.update { it.copy(isSubmitting = true, waitingForConnection = false, errorMessage = null) }

        val slowWatch = viewModelScope.launch {
            delay(slowWriteAfterMs)
            _state.update { if (it.isSubmitting) it.copy(waitingForConnection = true) else it }
        }
        viewModelScope.launch {
            val now = clock()
            val zone = zone()
            val place = PlanPlace(s.where.text.trim(), s.where.locationName, s.where.latitude, s.where.longitude)
            val activity = PlanWrites.typedActivity(name, now)
            try {
                val result = when (mode) {
                    is ComposerMode.Proposal -> {
                        val plan = PlanWrites.proposal(mode.matchID, myID, mode.receiverID, activity, place, s.date, now)
                        val summary = PlanWrites.proposalSummary(name, s.date, zone)
                        val planID = propose(plan) { id -> PlanWrites.proposalMessage(mode.matchID, myID, mode.receiverID, id, summary, now) }
                        ComposerResult.Proposed(mode.matchID, planID)
                    }
                    ComposerMode.OpenPost -> {
                        val time = ComposerTimes.postTime(s.dayChoice, s.date, now, zone)
                        _state.update { it.copy(date = time) }
                        val id = postTodayPlan(PlanWrites.todayPlan(myID, activity, time, place, now))
                        ComposerResult.Posted(postedPlan(id, activity, time, place, now))
                    }
                    ComposerMode.GroupInvite -> {
                        val invitees = s.inviteeOptions.map { it.userID }.filter { it in s.selectedInviteeIDs }
                        val id = createGroupPlan(PlanWrites.groupPlan(myID, invitees, activity, s.date, place, now))
                        ComposerResult.Invited(id)
                    }
                }
                slowWatch.cancel()
                _state.update { it.copy(isSubmitting = false, waitingForConnection = false, result = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                slowWatch.cancel()
                _state.update { it.copy(isSubmitting = false, waitingForConnection = false, errorMessage = failureMessage(e)) }
            }
        }
    }

    /** iOS's per-mode failure text. */
    private fun failureMessage(e: Exception): String = when (mode) {
        is ComposerMode.Proposal -> "Couldn't send proposal. Please try again."
        ComposerMode.OpenPost -> "Could not post plan: ${e.localizedMessage ?: e.toString()}"
        ComposerMode.GroupInvite -> "Couldn't send invites. Please try again."
    }

    private fun postedPlan(id: String, activity: Activity, time: Instant, place: PlanPlace, now: Instant) = TodayPlan(
        id = id, creatorID = myID, activity = activity, scheduledTime = time, note = null,
        location = place.location, locationName = place.locationName,
        locationLatitude = place.latitude, locationLongitude = place.longitude,
        status = TodayPlanStatus.OPEN, claimerID = null, createdAt = now, updatedAt = now,
        creatorReportedClaimer = null, claimerReportedCreator = null,
    )

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_SUGGESTIONS = 8

        fun factory(request: ComposerRequest): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AtxFriendsApp).container
                val myID = (container.session.state.value as? SessionState.Ready)?.user?.uid.orEmpty()
                PlanComposerViewModel(
                    request = request,
                    myID = myID,
                    fetchActivityNames = container.activities::fetchActivityNames,
                    fetchUser = container.users::fetchUser,
                    fetchMatches = container.matches::fetchMatches,
                    propose = container.plans::propose,
                    postTodayPlan = container.todayPlans::create,
                    createGroupPlan = container.groupPlans::create,
                    placeSearch = container.placeSearch,
                )
            }
        }
    }
}
