package com.georgeappdev.atxfriends.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.model.PlanStatus
import com.georgeappdev.atxfriends.data.model.TodayPlan
import com.georgeappdev.atxfriends.data.repository.MessageStore
import com.georgeappdev.atxfriends.data.repository.MessageWrites
import com.georgeappdev.atxfriends.data.repository.PlanStore
import com.georgeappdev.atxfriends.data.repository.PlanUpdates
import com.georgeappdev.atxfriends.data.repository.ShowUpStore
import com.georgeappdev.atxfriends.data.repository.ShowUpWrites
import com.georgeappdev.atxfriends.domain.messages.MessageDraft
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import com.georgeappdev.atxfriends.domain.messages.PlanCalendarEvent
import com.georgeappdev.atxfriends.domain.messages.ReadReceipts
import com.georgeappdev.atxfriends.domain.messages.RescheduleTime
import com.georgeappdev.atxfriends.domain.messages.canRespondToReschedule
import com.georgeappdev.atxfriends.domain.messages.isConfirmedOrReschedulePending
import com.georgeappdev.atxfriends.domain.messages.otherUserID
import com.georgeappdev.atxfriends.ui.matches.MatchUi
import com.georgeappdev.atxfriends.ui.plans.ComposerMode
import com.georgeappdev.atxfriends.ui.plans.ComposerRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** A change the user can make to a plan from the thread (spec §5.5). */
enum class PlanAction { ACCEPT, DECLINE, ACCEPT_RESCHEDULE, DECLINE_RESCHEDULE, CANCEL, REQUEST_RESCHEDULE }

data class ThreadUiState(
    val messagesLoaded: Boolean = false,
    /** The messages listener failed; shown as an error with Try Again (iOS shows nothing). */
    val messagesFailed: Boolean = false,
    /** Oldest first, live. */
    val messages: List<Message> = emptyList(),
    /** Messages sent from this device that the server hasn't accepted yet. */
    val pendingMessageIDs: Set<String> = emptySet(),
    val plansLoaded: Boolean = false,
    /** Every plan in this match, live. Empty if the plans listener failed (as on iOS). */
    val plans: List<Plan> = emptyList(),
    /** iOS starts the pinned card expanded, then collapses it once if the thread has messages. */
    val planCardExpanded: Boolean = true,
    val planCardInitialized: Boolean = false,
    /** The input bar's text (iOS `draftMessage`). */
    val draft: String = "",
    /** The draft is over the rules' 5000-character limit; holds its length. */
    val draftTooLong: Int? = null,
    /** The last send failed; its text is back in the draft. iOS sets an error it never shows. */
    val sendFailed: Boolean = false,
    /** Plans with a write in flight; their buttons are disabled until it finishes. */
    val busyPlanIDs: Set<String> = emptySet(),
    /** The plan change that just failed, shown as a "Couldn't Update Plan" alert. */
    val planError: PlanAction? = null,
    /** The plan whose "Cancel this plan?" alert is showing. */
    val confirmingCancelPlanID: String? = null,
    /** That alert was opened from an upcoming-plans row (its message names the activity). */
    val cancelFromList: Boolean = false,
    /** The plan the Reschedule sheet is open for. */
    val reschedulingPlanID: String? = null,
    /** The reschedule request is saving; the sheet's button shows a spinner. */
    val rescheduleSaving: Boolean = false,
    /** The last reschedule request failed; the sheet stays open with the user's pick. */
    val rescheduleFailed: Boolean = false,
    /** The picked time is no longer in the future. */
    val rescheduleTimePassed: Boolean = false,
    /** The "More upcoming plans" sheet is open. */
    val showingAllPlans: Boolean = false,
    /** Plans this device has already sent to the calendar ("Added to Calendar"). */
    val addedToCalendar: Set<String> = emptySet(),
    /** A claimed Today plan whose time has passed and still needs my show-up report. */
    val pendingShowUp: TodayPlan? = null,
    val showUpSaving: Boolean = false,
    /** The report didn't save: "Couldn't Record Report" (the card stays so it can be retried). */
    val showUpFailed: Boolean = false,
    /** The Match Detail sheet opened from the avatar; filled in further once the profile loads. */
    val profile: MatchUi? = null,
    /** The plan composer (android-09's ProposePlanSheet port), open in `.proposal` mode. */
    val composer: ComposerRequest? = null,
) {
    val plansByID: Map<String, Plan> get() = plans.associateBy { it.id }

    /** iOS `canSend`: something besides whitespace to send. */
    val canSend: Boolean get() = draft.isNotBlank()
}

/**
 * Port of the open-thread half of iOS MessagingViewModel: live messages, sending, read
 * receipts, plus a live
 * listener on the match's plans (which drives both the pinned card and the inline proposal
 * cards). Listeners stop when the thread closes (its ViewModelStore is cleared).
 */
class MessageThreadViewModel(
    val thread: MessageThread,
    private val myID: String,
    private val messageStore: MessageStore,
    private val planStore: PlanStore,
    private val calendarStore: AddedToCalendarStore,
    private val showUpStore: ShowUpStore,
    private val loadProfile: suspend (MessageThread) -> MatchUi? = { null },
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var messagesJob: Job? = null
    private var plansJob: Job? = null

    /** Read receipts being written, so each message is only flipped once at a time. */
    private val markingRead = mutableSetOf<String>()

    init {
        listenToMessages()
        listenToPlans()
        loadPendingShowUp()
    }

    fun retry() {
        _state.update { it.copy(messagesFailed = false, messagesLoaded = false) }
        listenToMessages()
        listenToPlans()
    }

    /** Called once the pinned card first appears: expanded only if there are no messages yet. */
    fun onPinnedPlanShown() = _state.update {
        if (it.planCardInitialized || !it.messagesLoaded) it
        else it.copy(planCardExpanded = it.messages.isEmpty(), planCardInitialized = true)
    }

    fun expandPlanCard() = _state.update { it.copy(planCardExpanded = true) }

    /** iOS collapses the pinned card when the message field gains focus. */
    fun onInputFocused() = _state.update { it.copy(planCardExpanded = false) }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text, draftTooLong = null) }

    fun dismissSendError() = _state.update { it.copy(sendFailed = false) }

    fun dismissTooLong() = _state.update { it.copy(draftTooLong = null) }

    /**
     * iOS `sendMessage`: trims the draft, clears the box right away, and writes the message. It
     * appears in the list at once (marked "Sending…") via the listener's local copy. If the write
     * fails, the text goes back in the box and an error shows. The box is cleared before the
     * write starts, so a second tap has nothing to send.
     */
    fun send() {
        val text = when (val prepared = MessageDraft.prepare(_state.value.draft)) {
            MessageDraft.Result.Empty -> return
            is MessageDraft.Result.TooLong -> {
                _state.update { it.copy(draftTooLong = prepared.length) }
                return
            }
            is MessageDraft.Result.Ready -> prepared.text
        }
        _state.update { it.copy(draft = "", draftTooLong = null, sendFailed = false) }
        val message = MessageWrites.newMessage(
            matchID = thread.id,
            senderID = myID,
            receiverID = thread.otherUserID,
            text = text,
            sentAt = clock(),
        )
        viewModelScope.launch {
            try {
                messageStore.send(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(draft = MessageDraft.restore(text, it.draft), sendFailed = true) }
            }
        }
    }

    // region Plan changes (spec §5.5). The live plans listener shows each result.

    /** PlanProposalCard "Accept": the receiver confirms a pending plan at its proposed time. */
    fun acceptProposal(planID: String) = changePlan(planID, PlanAction.ACCEPT) { plan, now ->
        if (plan.status == PlanStatus.PENDING && plan.receiverID == myID) PlanUpdates.accept(plan, now) else null
    }

    /** PlanProposalCard "Decline". */
    fun declineProposal(planID: String) = changePlan(planID, PlanAction.DECLINE) { plan, now ->
        if (plan.status == PlanStatus.PENDING && plan.receiverID == myID) PlanUpdates.decline(now) else null
    }

    /** "Accept" on a pending reschedule: the plan moves to the suggested time. */
    fun acceptReschedule(planID: String) = changePlan(planID, PlanAction.ACCEPT_RESCHEDULE) { plan, now ->
        if (plan.canRespondToReschedule(myID)) PlanUpdates.acceptReschedule(plan, now) else null
    }

    /** "Decline" on a pending reschedule: the plan stays at its original time. */
    fun declineReschedule(planID: String) = changePlan(planID, PlanAction.DECLINE_RESCHEDULE) { plan, now ->
        if (plan.canRespondToReschedule(myID)) PlanUpdates.declineReschedule(plan, now) else null
    }

    /** "Cancel plan" (pinned card ⋯ menu or the upcoming-plans sheet), after its confirm alert. */
    fun cancelPlan(planID: String) = changePlan(planID, PlanAction.CANCEL) { plan, now ->
        if (plan.isConfirmedOrReschedulePending) PlanUpdates.cancel(now) else null
    }

    fun dismissPlanError() = _state.update { it.copy(planError = null) }

    /** ⋯ → "Cancel plan", or "Cancel" on an upcoming-plans row: asks first ("Cancel this plan?"). */
    fun askToCancel(planID: String, fromList: Boolean) =
        _state.update { it.copy(confirmingCancelPlanID = planID, cancelFromList = fromList) }

    fun dismissCancel() = _state.update { it.copy(confirmingCancelPlanID = null) }

    /** "Cancel Plan" in the confirm alert. */
    fun confirmCancel() {
        val planID = _state.value.confirmingCancelPlanID ?: return
        _state.update { it.copy(confirmingCancelPlanID = null) }
        cancelPlan(planID)
    }

    /** ⋯ → "Reschedule". Only offered for a confirmed plan with no request pending, as on iOS. */
    fun openReschedule(planID: String) {
        val plan = _state.value.plansByID[planID] ?: return
        if (plan.status != PlanStatus.CONFIRMED) return
        _state.update {
            it.copy(reschedulingPlanID = planID, rescheduleFailed = false, rescheduleTimePassed = false, rescheduleSaving = false)
        }
    }

    /** "Cancel" on the sheet. Ignored while the request is saving. */
    fun closeReschedule() = _state.update { if (it.rescheduleSaving) it else it.copy(reschedulingPlanID = null) }

    /** The time the Reschedule sheet starts on (iOS ReschedulePlanSheet.init). */
    fun rescheduleInitialTime(): Instant? {
        val plan = _state.value.reschedulingPlanID?.let { _state.value.plansByID[it] } ?: return null
        return RescheduleTime.initial(plan, clock())
    }

    /**
     * "Suggest New Time": writes the reschedule request (iOS `counterPropose(newDates: [date])`)
     * and closes the sheet once it's saved. On failure the sheet stays open with the same pick
     * and an error (iOS closes the sheet either way and never says it failed).
     */
    fun requestReschedule(newTime: Instant) {
        val current = _state.value
        val planID = current.reschedulingPlanID ?: return
        if (current.rescheduleSaving) return
        val now = clock()
        if (!RescheduleTime.isAllowed(newTime, now)) {
            _state.update { it.copy(rescheduleTimePassed = true, rescheduleFailed = false) }
            return
        }
        val plan = current.plansByID[planID]
        if (plan == null || plan.status != PlanStatus.CONFIRMED) {
            // The plan changed underneath the sheet (cancelled, or the other person asked first).
            _state.update { it.copy(rescheduleFailed = true, rescheduleTimePassed = false) }
            return
        }
        val update = PlanUpdates.requestReschedule(newTime, myID, now)
        _state.update { it.copy(rescheduleSaving = true, rescheduleFailed = false, rescheduleTimePassed = false) }
        viewModelScope.launch {
            val failed = try {
                planStore.update(planID, update)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            _state.update {
                if (failed) it.copy(rescheduleSaving = false, rescheduleFailed = true)
                else it.copy(rescheduleSaving = false, reschedulingPlanID = null)
            }
        }
    }

    /** What "Add to Calendar" hands the calendar app (iOS CalendarEventFields). */
    fun calendarEvent(plan: Plan): PlanCalendarEvent = PlanCalendarEvent.of(plan, thread.otherUserName, clock())

    /**
     * Back from the calendar app's new-event screen. Calendar apps don't reliably report whether
     * the event was saved, so returning counts as added, and the button says "Added to Calendar".
     */
    fun onReturnedFromCalendar(planID: String) {
        calendarStore.markAdded(planID)
        _state.update { it.copy(addedToCalendar = it.addedToCalendar + planID) }
    }

    // region Show-up report ("How did it go?")

    /** iOS `loadPendingShowUpReport`, once when the thread opens. A failed lookup shows no card, as on iOS. */
    private fun loadPendingShowUp() {
        viewModelScope.launch {
            val plan = orNull { showUpStore.fetchPendingReport(myID, thread.otherUserID, clock()) }
            _state.update { it.copy(pendingShowUp = plan) }
        }
    }

    /**
     * "They showed up!" / "Didn't show": queues the report for the Cloud Function (iOS
     * `submitShowUpReport`). The card goes away once it's saved; on failure it stays, with an
     * alert, so the user can try again. Ignored while one is saving.
     */
    fun submitShowUp(didShowUp: Boolean) {
        val current = _state.value
        val plan = current.pendingShowUp ?: return
        if (current.showUpSaving) return
        val reportedUserID = plan.otherUserID(myID)?.takeIf { it.isNotEmpty() } ?: return
        val report = ShowUpWrites.report(plan.id, myID, reportedUserID, didShowUp, clock())
        _state.update { it.copy(showUpSaving = true, showUpFailed = false) }
        viewModelScope.launch {
            val failed = try {
                showUpStore.submit(report)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            _state.update {
                if (failed) it.copy(showUpSaving = false, showUpFailed = true)
                else it.copy(showUpSaving = false, pendingShowUp = null)
            }
        }
    }

    /** The card's ✕: hides it until the thread is opened again, as on iOS. */
    fun dismissShowUp() = _state.update { if (it.showUpSaving) it else it.copy(pendingShowUp = null) }

    fun dismissShowUpError() = _state.update { it.copy(showUpFailed = false) }

    // endregion

    // region Match Detail (avatar)

    private var fullProfile: MatchUi? = null
    private var profileJob: Job? = null

    /**
     * The top bar's avatar (or the empty state's photo): opens Match Detail right away with what
     * the thread knows, then fills in photos, distance, show-up meter, and Simpatico once loaded.
     */
    fun openProfile() {
        _state.update { it.copy(profile = fullProfile ?: ThreadProfile.basic(thread)) }
        if (fullProfile != null || profileJob?.isActive == true) return
        profileJob = viewModelScope.launch {
            val loaded = orNull { loadProfile(thread) } ?: return@launch
            fullProfile = loaded
            _state.update { if (it.profile != null) it.copy(profile = loaded) else it }
        }
    }

    fun closeProfile() = _state.update { it.copy(profile = null) }

    /**
     * Match Detail's "Propose a Plan": iOS closes the profile first, then opens the composer, so
     * one sheet never sits on top of another.
     */
    fun proposeFromProfile() = _state.update {
        it.copy(profile = null, composer = ComposerRequest(ComposerMode.Proposal(thread.id, thread.otherUserID)))
    }

    // endregion

    // region Plan composer

    /**
     * The input bar's "+" and the empty state's "Propose a plan" open the composer blank; a
     * "You both like" chip opens it with that activity filled in (iOS proposePlanPrefillActivity).
     * The new proposal then shows up through the live listeners.
     */
    fun openComposer(activityName: String? = null) = _state.update {
        it.copy(composer = ComposerRequest(ComposerMode.Proposal(thread.id, thread.otherUserID), initialActivity = activityName))
    }

    fun closeComposer() = _state.update { it.copy(composer = null) }

    // endregion

    /** The pinned card's "+N more" chip. */
    fun showAllPlans() = _state.update { it.copy(showingAllPlans = true) }

    fun hideAllPlans() = _state.update { it.copy(showingAllPlans = false) }

    /**
     * Writes [write]'s change to the current copy of the plan. Ignored while that plan already
     * has a write in flight (double taps) or when [write] returns null (the plan moved on, or
     * iOS would do nothing). On failure the alert names [action].
     */
    private fun changePlan(planID: String, action: PlanAction, write: (Plan, Instant) -> DocumentUpdate?): Boolean {
        val current = _state.value
        if (planID in current.busyPlanIDs) return false
        val plan = current.plansByID[planID] ?: return false
        val update = write(plan, clock()) ?: return false
        _state.update { it.copy(busyPlanIDs = it.busyPlanIDs + planID, planError = null) }
        viewModelScope.launch {
            val failed = try {
                planStore.update(planID, update)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            _state.update {
                it.copy(busyPlanIDs = it.busyPlanIDs - planID, planError = if (failed) action else it.planError)
            }
        }
        return true
    }

    // endregion

    private fun listenToMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageStore.messagesFor(thread.id)
                .catch { _state.update { it.copy(messagesFailed = true) } }
                .collect { live ->
                    _state.update {
                        it.copy(
                            messages = live.messages,
                            pendingMessageIDs = live.pendingIDs,
                            messagesLoaded = true,
                            messagesFailed = false,
                        )
                    }
                    markIncomingRead(live.messages)
                }
        }
    }

    /**
     * Marks unread messages to me as read whenever the messages load or change, as iOS does
     * while a thread is open. A failed receipt isn't shown (there's nothing for the user to do);
     * it's retried on the next listener update, and the unread dot simply stays until then.
     */
    private fun markIncomingRead(messages: List<Message>) {
        for (id in ReadReceipts.toMark(messages, myID, markingRead)) {
            markingRead += id
            viewModelScope.launch {
                try {
                    messageStore.markRead(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Retried on the next update.
                } finally {
                    markingRead -= id
                }
            }
        }
    }

    private fun listenToPlans() {
        plansJob?.cancel()
        plansJob = viewModelScope.launch {
            planStore.plansFor(thread.id)
                .catch { _state.update { it.copy(plans = emptyList(), plansLoaded = true) } }
                .collect { plans ->
                    val added = plans.filter { calendarStore.isAdded(it.id) }.mapTo(mutableSetOf()) { it.id }
                    _state.update { it.copy(plans = plans, plansLoaded = true, addedToCalendar = added) }
                }
        }
    }

    companion object {
        fun factory(
            thread: MessageThread,
            myID: String,
            messages: MessageStore,
            plans: PlanStore,
            calendar: AddedToCalendarStore,
            showUps: ShowUpStore,
            profiles: ThreadProfileLoader,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { MessageThreadViewModel(thread, myID, messages, plans, calendar, showUps, profiles::load) } }
    }
}
