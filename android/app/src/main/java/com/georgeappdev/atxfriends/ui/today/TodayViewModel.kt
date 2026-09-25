package com.georgeappdev.atxfriends.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.matching.showUpMeter
import com.georgeappdev.atxfriends.navigation.ThreadRequest
import com.georgeappdev.atxfriends.domain.openslots.ActivitySuggestionTier
import com.georgeappdev.atxfriends.domain.openslots.OpenSlot
import com.georgeappdev.atxfriends.domain.openslots.OpenSlotGenerator
import com.georgeappdev.atxfriends.domain.openslots.RankedOpenSlot
import com.georgeappdev.atxfriends.domain.today.OpenSlotsHeader
import com.georgeappdev.atxfriends.domain.today.TodayClaim
import com.georgeappdev.atxfriends.domain.today.TodayFeed
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One card in the open-plans feed. */
data class TodayPlanUi(
    val id: String,
    val activityName: String,
    val scheduledTime: Instant,
    /** Null until the poster's profile loads (iOS shows "…"). */
    val posterName: String?,
    val showUpMeter: String,
    val location: String?,
    val note: String?,
    val isOwnPlan: Boolean,
)

data class TodayUiState(
    val isLoading: Boolean = true,
    val availableActivities: List<String> = emptyList(),
    /** Null = "All". */
    val activityFilter: String? = null,
    val plans: List<TodayPlanUi> = emptyList(),
    val openSlots: List<RankedOpenSlot> = emptyList(),
    val openSlotsHeader: OpenSlotsHeader = OpenSlotsHeader.YOUR_OPEN_SLOTS,
    val nextUsualSlot: OpenSlot? = null,
    /** Activity name for "Someone claimed your {activity} plan! 🎉". Kept after hiding so the exit animation keeps its text. */
    val claimedBannerActivity: String? = null,
    val isClaimedBannerVisible: Boolean = false,
    /** Detail for "Could not load plans: …"; null when no error is showing. */
    val loadError: String? = null,
    /** "Posted!" after posting a plan; hides itself after 2 seconds. */
    val showPostedToast: Boolean = false,
    /** Plans whose "I'm in" is saving ("Joining…"). */
    val claimingIDs: Set<String> = emptySet(),
    /** A failed claim's message for the "Something went wrong" alert (iOS errorMessage). */
    val actionError: String? = null,
    /** Set after a successful claim: the screen opens this thread, then calls [TodayViewModel.onThreadOpened]. */
    val threadToOpen: ThreadRequest? = null,
    /** The clock the time labels are computed against; advances while the screen is visible. */
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Port of iOS TodayViewModel. Like iOS, each appearance runs a one-time load and then attaches
 * the realtime listener; leaving the screen removes the listener. Posting happens in the plan
 * composer, which hands the new plan back through [onPosted].
 */
class TodayViewModel(
    private val myID: String,
    private val fetchOpenPlans: suspend () -> List<TodayPlan>,
    private val openPlansUpdates: () -> Flow<List<TodayPlan>>,
    private val fetchUser: suspend (uid: String) -> UserDoc,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** TodayClaim.claim for [TodayPlan] by the given claimer; returns the match ID. */
    private val claimPlan: suspend (plan: TodayPlan, claimerID: String) -> String = { _, _ -> error("Claiming isn't available") },
) : ViewModel() {

    private data class PosterInfo(val displayName: String, val showUpMeter: String)

    private val _state = MutableStateFlow(TodayUiState(now = clock(), zone = zone()))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var openPlans: List<TodayPlan> = emptyList()
    private var posterInfo: Map<String, PosterInfo> = emptyMap()
    /** The user's own profile, for the ghost-card generator. Loaded once, like iOS. */
    private var myProfile: UserProfile? = null
    private var activityFilter: String? = null

    private var visibleJob: Job? = null
    private var refreshJob: Job? = null
    private var bannerJob: Job? = null
    private var toastJob: Job? = null

    /** iOS `.task`: load, then attach the listener; plus the clock that re-checks expiry. */
    fun onAppear() {
        visibleJob?.cancel()
        visibleJob = viewModelScope.launch {
            launch { tick() }
            load()
            listen()
        }
    }

    /** iOS `.onDisappear`: removes the listener. */
    fun onDisappear() {
        visibleJob?.cancel()
        visibleJob = null
    }

    /** Pull to refresh: the same one-time load (iOS `.refreshable`). */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { load() }
    }

    fun selectAll() {
        activityFilter = null
        publish()
    }

    /** Tapping the selected chip again clears the filter, as on iOS. */
    fun selectActivity(name: String) {
        activityFilter = if (activityFilter == name) null else name
        publish()
    }

    fun dismissError() = _state.update { it.copy(loadError = null) }

    /**
     * iOS `claimPlan`: claim atomically, drop the card, then open the new thread with the
     * poster. Ignores taps on your own post or on a plan already being claimed. On failure the
     * button resets and the error shows (e.g. "someone got there first").
     */
    fun claim(planID: String) {
        val plan = openPlans.firstOrNull { it.id == planID } ?: return
        if (myID.isEmpty() || plan.creatorID == myID || planID in _state.value.claimingIDs) return
        _state.update { it.copy(claimingIDs = it.claimingIDs + planID) }
        viewModelScope.launch {
            try {
                val matchID = claimPlan(plan, myID)
                openPlans = openPlans.filterNot { it.id == planID }
                val name = posterInfo[plan.creatorID]?.displayName.orEmpty()
                _state.update { it.copy(threadToOpen = ThreadRequest(matchID, plan.creatorID, name)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionError = e.localizedMessage ?: e.toString()) }
            } finally {
                _state.update { it.copy(claimingIDs = it.claimingIDs - planID) }
                publish()
            }
        }
    }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }

    fun onThreadOpened() = _state.update { it.copy(threadToOpen = null) }

    /**
     * The composer posted [plan]: show it right away (the listener confirms it on the next
     * snapshot) and flash "Posted!" for 2 seconds, as iOS does.
     */
    fun onPosted(plan: TodayPlan) {
        if (openPlans.none { it.id == plan.id }) openPlans = (openPlans + plan).sortedBy { it.scheduledTime }
        publish()
        _state.update { it.copy(showPostedToast = true) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(TOAST_MILLIS)
            _state.update { it.copy(showPostedToast = false) }
        }
    }

    private suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        try {
            val plans = fetchOpenPlans()
            openPlans = plans
            prefetchPosterInfo(plans)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loadError = e.localizedMessage ?: e.toString()) }
        }
        loadMyProfileIfNeeded()
        publish(isLoading = false)
    }

    private suspend fun loadMyProfileIfNeeded() {
        if (myProfile != null || myID.isEmpty()) return
        myProfile = (attempt { fetchUser(myID) } as? UserDoc.Found)?.profile
    }

    private suspend fun listen() {
        openPlansUpdates().collect { newPlans ->
            // The query only returns open plans, so one of my non-expired plans that vanished
            // was claimed by someone else.
            TodayFeed.justClaimed(openPlans, newPlans, myID, clock())?.let { showClaimedBanner(it.activity.name) }
            openPlans = newPlans
            publish()
            viewModelScope.launch { prefetchPosterInfo(newPlans) }
        }
    }

    private fun showClaimedBanner(activityName: String) {
        _state.update { it.copy(claimedBannerActivity = activityName, isClaimedBannerVisible = true) }
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(BANNER_MILLIS)
            _state.update { it.copy(isClaimedBannerVisible = false) }
        }
    }

    /**
     * Re-publishes once a minute (iOS's TimelineView tick for the "In 3 hrs" labels), and also
     * right when the next plan's start time passes so it leaves the feed on time.
     */
    private suspend fun tick() {
        while (true) {
            val now = clock()
            val nextExpiry = openPlans.map { it.scheduledTime }.filter { it >= now }.minOrNull()
            val untilExpiry = nextExpiry?.let { Duration.between(now, it).toMillis() + 1 } ?: Long.MAX_VALUE
            delay(minOf(TICK_MILLIS, untilExpiry))
            publish()
        }
    }

    /** Sequential, skipping people already cached; a failed fetch leaves "…" / "New", like iOS. */
    private suspend fun prefetchPosterInfo(plans: List<TodayPlan>) {
        val unknown = plans.map { it.creatorID }.distinct().filter { it !in posterInfo }
        for (id in unknown) {
            val user = (attempt { fetchUser(id) } as? UserDoc.Found)?.profile ?: continue
            posterInfo = posterInfo + (id to PosterInfo(user.displayName, showUpMeter(user.showUpThumbsUp, user.showUpTotal)))
            publish()
        }
    }

    private fun publish(isLoading: Boolean = _state.value.isLoading) {
        val now = clock()
        val zone = zone()
        val filtered = TodayFeed.filteredPlans(openPlans, activityFilter, now)
        val profile = myProfile
        val slots = if (profile == null) emptyList() else OpenSlotGenerator.generateForToday(
            daySlotCombos = profile.daySlotCombos,
            activities = OpenSlotGenerator.activities(profile.activities.map { it.name }, ActivitySuggestionTier.SPONTANEOUS),
            now = now,
            existingPlanStarts = openPlans.filter { it.creatorID == myID }.map { it.scheduledTime },
            zone = zone,
        )
        val nextUsual = profile?.let {
            OpenSlotGenerator.nextUsualOccurrence(it.daySlotCombos, now, now.plus(Duration.ofDays(7)), zone)
        }
        _state.update {
            it.copy(
                isLoading = isLoading,
                availableActivities = TodayFeed.availableActivities(openPlans, now),
                activityFilter = activityFilter,
                plans = filtered.map(::toUi),
                openSlots = slots,
                openSlotsHeader = TodayFeed.openSlotsHeader(filtered, slots),
                nextUsualSlot = nextUsual,
                now = now,
                zone = zone,
            )
        }
    }

    private fun toUi(plan: TodayPlan): TodayPlanUi {
        val poster = posterInfo[plan.creatorID]
        return TodayPlanUi(
            id = plan.id,
            activityName = plan.activity.name,
            scheduledTime = plan.scheduledTime,
            posterName = poster?.displayName,
            showUpMeter = poster?.showUpMeter ?: showUpMeter(0, 0),
            location = plan.location?.takeIf { it.isNotEmpty() },
            note = plan.note?.takeIf { it.isNotEmpty() },
            isOwnPlan = plan.creatorID == myID,
        )
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        const val TICK_MILLIS = 60_000L
        /** iOS dismisses the claimed banner 3.5 seconds after it appears. */
        const val BANNER_MILLIS = 3_500L
        /** iOS hides the "Posted!" toast 2 seconds after it appears. */
        const val TOAST_MILLIS = 2_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AtxFriendsApp).container
                val myID = (container.session.state.value as? SessionState.Ready)?.user?.uid.orEmpty()
                TodayViewModel(
                    myID = myID,
                    fetchOpenPlans = container.todayPlans::fetchOpenPlans,
                    openPlansUpdates = container.todayPlans::openPlans,
                    fetchUser = container.users::fetchUser,
                    claimPlan = { plan, claimerID ->
                        TodayClaim.claim(container.todayPlans, plan.id, claimerID, plan.creatorID, plan.activity, plan.scheduledTime)
                    },
                )
            }
        }
    }
}
