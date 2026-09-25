package com.georgeappdev.atxfriends.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.GroupPlan
import com.georgeappdev.atxfriends.data.model.GroupPlanResponse
import com.georgeappdev.atxfriends.data.model.GroupPlanStatus
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.GroupPlanStore
import com.georgeappdev.atxfriends.data.repository.PlanWrites
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.openslots.OpenSlot
import com.georgeappdev.atxfriends.domain.upcoming.DayDot
import com.georgeappdev.atxfriends.domain.upcoming.MyPlanStatus
import com.georgeappdev.atxfriends.domain.upcoming.UpcomingSchedule
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One `GroupPlanCard`. Names are null until that profile loads (iOS shows "…"). */
data class GroupPlanUi(
    val id: String,
    val activityName: String,
    val date: Instant,
    val hostName: String?,
    val location: String?,
    val goingCount: Int,
    val myStatus: MyPlanStatus,
)

data class UpcomingDayUi(val date: LocalDate, val dot: DayDot, val plans: List<GroupPlanUi>, val ghost: OpenSlot?)

data class InviteeUi(val userID: String, val name: String?, val response: GroupPlanResponse?)

/** GroupPlanDetailView's content, always read from the live listener. */
data class GroupPlanDetailUi(
    val id: String,
    val activityName: String,
    val date: Instant,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val isCancelled: Boolean,
    val hostName: String?,
    val invitees: List<InviteeUi>,
    val isHost: Boolean,
    val isInvitee: Boolean,
    val myResponse: GroupPlanResponse?,
)

/**
 * The invite just sent from the composer. [plan] is null until the live listener delivers it
 * (a moment later), and [sectionKey] is the list item it sits in, for scrolling to it.
 */
data class SentInvite(val planID: String, val plan: GroupPlanUi?, val sectionKey: String?)

/** iOS's two write-failure messages: "Couldn't update your response…" / "Couldn't cancel the plan…". */
enum class UpcomingError { RESPOND, CANCEL }

data class UpcomingUiState(
    val isLoading: Boolean = true,
    /** Android-only: the listener failed (iOS keeps spinning forever). */
    val loadFailed: Boolean = false,
    val hasAnyContent: Boolean = false,
    /** Plans later today, above the strip. */
    val todayPlans: List<GroupPlanUi> = emptyList(),
    val days: List<UpcomingDayUi> = emptyList(),
    /** Plans past the strip's last day, below it. */
    val laterPlans: List<GroupPlanUi> = emptyList(),
    /** An invite this device just sent: highlighted, scrolled to, and confirmed in a banner. */
    val justSent: SentInvite? = null,
    val detail: GroupPlanDetailUi? = null,
    val isResponding: Boolean = false,
    val isCancelling: Boolean = false,
    /** A failed response or cancel, for the "Something went wrong" alert (iOS errorMessage). */
    val actionError: UpcomingError? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Port of iOS GroupPlansViewModel + UpcomingView's derived layout. While on screen it keeps a
 * live listener on every group plan the user hosts or is invited to (removed on leaving), a
 * small name cache, and the user's own profile for ghost slots.
 */
/** List item keys for [UpcomingScreen]'s sections, so a just-sent invite can be scrolled to. */
const val SECTION_TODAY = "today"
const val SECTION_LATER = "later"
fun daySectionKey(date: LocalDate) = "day-$date"

class UpcomingViewModel(
    private val myID: String,
    private val store: GroupPlanStore,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val _state = MutableStateFlow(UpcomingUiState(zone = zone()))
    val state: StateFlow<UpcomingUiState> = _state.asStateFlow()

    private var groupPlans: List<GroupPlan> = emptyList()
    private var names: Map<String, String> = emptyMap()
    private var myProfile: UserProfile? = null
    private var selectedPlanID: String? = null
    private var listenJob: Job? = null

    /** iOS `.onAppear { startListening() }`. */
    fun onAppear() {
        if (myID.isEmpty()) return
        listenJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        listenJob = viewModelScope.launch {
            launch { loadMyProfileIfNeeded() }
            try {
                store.groupPlansFor(myID).collect { plans ->
                    groupPlans = plans
                    _state.update { it.copy(loadFailed = false) }
                    publish(isLoading = false)
                    loadMissingNames()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    /** iOS `.onDisappear { stopListening() }`. */
    fun onDisappear() {
        listenJob?.cancel()
        listenJob = null
    }

    fun retry() = onAppear()

    private var justSentID: String? = null

    /** The composer saved an invite: confirm it, and highlight it once it's in the list. */
    fun onInviteSent(planID: String) {
        justSentID = planID
        publish()
    }

    /** The confirmation has been shown long enough. */
    fun clearJustSent() {
        justSentID = null
        _state.update { it.copy(justSent = null) }
    }

    fun openDetail(planID: String) {
        selectedPlanID = planID
        publish()
    }

    fun closeDetail() {
        selectedPlanID = null
        _state.update { it.copy(detail = null) }
    }

    fun dismissError() = _state.update { it.copy(actionError = null) }

    /** iOS `respond(to:response:)`: writes only my own `responses.{uid}` + `updatedAt`. */
    fun respond(response: GroupPlanResponse) {
        val planID = selectedPlanID ?: return
        if (_state.value.isResponding) return
        _state.update { it.copy(isResponding = true) }
        viewModelScope.launch {
            try {
                store.respond(planID, PlanWrites.groupPlanResponse(myID, response, clock()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionError = UpcomingError.RESPOND) }
            } finally {
                _state.update { it.copy(isResponding = false) }
            }
        }
    }

    /** iOS `cancel(_:)` (host only): on success the detail closes. */
    fun cancelPlan() {
        val planID = selectedPlanID ?: return
        if (_state.value.isCancelling) return
        _state.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            try {
                store.cancel(planID, PlanWrites.groupPlanCancel(clock()))
                closeDetail()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionError = UpcomingError.CANCEL) }
            } finally {
                _state.update { it.copy(isCancelling = false) }
            }
        }
    }

    private suspend fun loadMyProfileIfNeeded() {
        if (myProfile != null) return
        myProfile = (attempt { fetchUser(myID) } as? UserDoc.Found)?.profile
        publish()
    }

    /** Host and invitee names, one at a time, skipping people already cached (iOS `loadMissingUsers`). */
    private suspend fun loadMissingNames() {
        val needed = groupPlans.flatMap { listOf(it.hostID) + it.inviteeIDs }.distinct().filter { it !in names }
        for (id in needed) {
            val profile = (attempt { fetchUser(id) } as? UserDoc.Found)?.profile ?: continue
            names = names + (id to profile.displayName)
            publish()
        }
    }

    private fun publish(isLoading: Boolean = _state.value.isLoading) {
        val now = clock()
        val zone = zone()
        val upcoming = UpcomingSchedule.upcomingPlans(groupPlans, myID, now)
        val profile = myProfile
        val slots = if (profile == null) emptyMap() else UpcomingSchedule.openSlotsByDay(
            profile.daySlotCombos, profile.activities.map { it.name }, upcoming, now, zone,
        )
        val days = UpcomingSchedule.days(upcoming, slots, now, zone).map { day ->
            UpcomingDayUi(day.date, day.dot, day.plans.map(::cardUi), day.ghost)
        }
        val todayPlans = UpcomingSchedule.todayPlans(upcoming, now, zone).map(::cardUi)
        val laterPlans = UpcomingSchedule.laterPlans(upcoming, now, zone).map(::cardUi)
        val sent = justSentID?.let { id ->
            val section = when {
                todayPlans.any { it.id == id } -> SECTION_TODAY
                laterPlans.any { it.id == id } -> SECTION_LATER
                else -> days.firstOrNull { d -> d.plans.any { it.id == id } }?.let { daySectionKey(it.date) }
            }
            val card = (todayPlans + laterPlans + days.flatMap { it.plans }).firstOrNull { it.id == id }
            SentInvite(id, card, section)
        }
        _state.update {
            it.copy(
                isLoading = isLoading,
                hasAnyContent = UpcomingSchedule.hasAnyContent(upcoming, slots),
                todayPlans = todayPlans,
                days = days,
                laterPlans = laterPlans,
                justSent = sent,
                detail = selectedPlanID?.let { id -> groupPlans.firstOrNull { p -> p.id == id }?.let(::detailUi) ?: it.detail },
                zone = zone,
            )
        }
    }

    private fun cardUi(plan: GroupPlan) = GroupPlanUi(
        id = plan.id,
        activityName = plan.activity.name,
        date = plan.date,
        hostName = names[plan.hostID],
        location = plan.location?.takeIf { it.isNotEmpty() },
        goingCount = UpcomingSchedule.goingCount(plan),
        myStatus = UpcomingSchedule.myStatus(plan, myID),
    )

    private fun detailUi(plan: GroupPlan) = GroupPlanDetailUi(
        id = plan.id,
        activityName = plan.activity.name,
        date = plan.date,
        location = plan.location,
        latitude = plan.locationLatitude,
        longitude = plan.locationLongitude,
        isCancelled = plan.status == GroupPlanStatus.CANCELLED,
        hostName = names[plan.hostID],
        invitees = plan.inviteeIDs.map { InviteeUi(it, names[it], plan.responses[it]) },
        isHost = plan.hostID == myID,
        isInvitee = myID in plan.inviteeIDs,
        myResponse = plan.responses[myID],
    )

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AtxFriendsApp).container
                val myID = (container.session.state.value as? SessionState.Ready)?.user?.uid.orEmpty()
                UpcomingViewModel(myID = myID, store = container.groupPlans, fetchUser = container.users::fetchUser)
            }
        }
    }
}
