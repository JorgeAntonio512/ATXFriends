package com.georgeappdev.atxfriends.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.data.model.Message
import com.georgeappdev.atxfriends.data.model.Plan
import com.georgeappdev.atxfriends.data.repository.MessageRepository
import com.georgeappdev.atxfriends.data.repository.PlanRepository
import com.georgeappdev.atxfriends.domain.messages.MessageThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThreadUiState(
    val messagesLoaded: Boolean = false,
    /** The messages listener failed; shown as an error with Try Again (iOS shows nothing). */
    val messagesFailed: Boolean = false,
    /** Oldest first, live. */
    val messages: List<Message> = emptyList(),
    val plansLoaded: Boolean = false,
    /** Every plan in this match, live. Empty if the plans listener failed (as on iOS). */
    val plans: List<Plan> = emptyList(),
    /** iOS starts the pinned card expanded, then collapses it once if the thread has messages. */
    val planCardExpanded: Boolean = true,
    val planCardInitialized: Boolean = false,
) {
    val plansByID: Map<String, Plan> get() = plans.associateBy { it.id }
}

/**
 * Port of the open-thread half of iOS MessagingViewModel: live messages plus a live listener on
 * the match's plans (which drives both the pinned card and the inline proposal cards).
 * Read-only: nothing is marked read, sent, accepted or declined. Listeners stop when the
 * thread closes (its ViewModelStore is cleared).
 */
class MessageThreadViewModel(
    val thread: MessageThread,
    private val messageRepo: MessageRepository,
    private val planRepo: PlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var messagesJob: Job? = null
    private var plansJob: Job? = null

    init {
        listenToMessages()
        listenToPlans()
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

    private fun listenToMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageRepo.messagesFor(thread.id)
                .catch { _state.update { it.copy(messagesFailed = true) } }
                .collect { messages ->
                    _state.update { it.copy(messages = messages, messagesLoaded = true, messagesFailed = false) }
                }
        }
    }

    private fun listenToPlans() {
        plansJob?.cancel()
        plansJob = viewModelScope.launch {
            planRepo.plansFor(thread.id)
                .catch { _state.update { it.copy(plans = emptyList(), plansLoaded = true) } }
                .collect { plans -> _state.update { it.copy(plans = plans, plansLoaded = true) } }
        }
    }

    companion object {
        fun factory(thread: MessageThread, messages: MessageRepository, plans: PlanRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { MessageThreadViewModel(thread, messages, plans) } }
    }
}
