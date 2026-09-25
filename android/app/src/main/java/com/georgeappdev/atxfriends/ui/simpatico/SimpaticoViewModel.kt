package com.georgeappdev.atxfriends.ui.simpatico

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.SimpaticoAnswer
import com.georgeappdev.atxfriends.data.model.SimpaticoImportance
import com.georgeappdev.atxfriends.data.repository.SimpaticoStore
import com.georgeappdev.atxfriends.domain.simpatico.SimpaticoQuestion
import com.georgeappdev.atxfriends.domain.simpatico.SimpaticoQuestions
import com.georgeappdev.atxfriends.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** The iOS error strings, in order: load, save (Next), finish (last question). */
enum class SimpaticoError { LOAD, SAVE, FINISH }

data class SimpaticoUiState(
    val isLoading: Boolean = true,
    /** Saved answers, keyed by question ID; a skipped question is absent. */
    val answers: Map<String, SimpaticoAnswer> = emptyMap(),
    val completedAt: Instant? = null,
    val hasLegacyAnswers: Boolean = false,
    val currentIndex: Int = 0,
    val selectedAnswerID: String? = null,
    val selectedAcceptable: Set<String> = emptySet(),
    val selectedImportance: SimpaticoImportance? = null,
    val isSaving: Boolean = false,
    val error: SimpaticoError? = null,
    /** Re-entered from "Edit answers", so the flow shows even though [completedAt] is set. */
    val isEditing: Boolean = false,
    /** Start was tapped on the intro (iOS keeps this as view @State). */
    val introDismissed: Boolean = false,
) {
    val questions: List<SimpaticoQuestion> get() = SimpaticoQuestions.all
    val currentQuestion: SimpaticoQuestion get() = questions[currentIndex.coerceIn(0, questions.size - 1)]
    val totalCount: Int get() = questions.size
    val answeredCount: Int get() = answers.size
    val canGoBack: Boolean get() = currentIndex > 0
    val isLastQuestion: Boolean get() = currentIndex == totalCount - 1
    val isComplete: Boolean get() = completedAt != null && !isEditing

    /** Every "I'm good with…" option is checked: importance is hidden and the weight is 0. */
    val doesNotMatter: Boolean get() = selectedAcceptable.size == currentQuestion.options.size

    val canAdvance: Boolean get() = selectedAnswerID != null && (doesNotMatter || selectedImportance != null)

    /** The user answered the old questionnaire but hasn't started v2 yet. */
    val showUpgradeBanner: Boolean get() = hasLegacyAnswers && answers.isEmpty()

    /** iOS shows the intro only while there are zero saved answers; after that the flow resumes directly. */
    val showIntro: Boolean get() = answers.isEmpty() && !introDismissed
}

/** Port of iOS `SimpaticoViewModel`. Loads once, one-time fetch (no listener), like iOS. */
class SimpaticoViewModel(
    private val store: SimpaticoStore,
    private val positions: SimpaticoPositionStore,
    private val currentUid: () -> String?,
) : ViewModel() {

    private val _state = MutableStateFlow(SimpaticoUiState())
    val state: StateFlow<SimpaticoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val uid = currentUid() ?: run { _state.update { it.copy(isLoading = false) }; return }
        _state.update { it.copy(isLoading = true) }
        try {
            val saved = store.fetchState(uid)
            _state.update { s ->
                val index = if (saved.completedAt == null) positions.get(uid).coerceIn(0, s.totalCount - 1) else s.currentIndex
                s.copy(
                    answers = saved.answers,
                    completedAt = saved.completedAt,
                    hasLegacyAnswers = saved.hasLegacyAnswers,
                    currentIndex = index,
                ).synced()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load Simpatico answers", e)
            _state.update { it.copy(error = SimpaticoError.LOAD) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun start() = _state.update { it.copy(introDismissed = true) }

    fun goBack() {
        val s = _state.value
        if (!s.canGoBack) return
        _state.update { it.copy(currentIndex = it.currentIndex - 1, error = null).synced() }
        currentUid()?.let { positions.save(it, _state.value.currentIndex) }
    }

    /** Picking your own answer always adds it to the acceptable set, like iOS. */
    fun selectAnswer(optionID: String) = _state.update {
        it.copy(selectedAnswerID = optionID, selectedAcceptable = it.selectedAcceptable + optionID)
    }

    fun toggleAcceptable(optionID: String) = _state.update {
        val acceptable = if (optionID in it.selectedAcceptable) it.selectedAcceptable - optionID else it.selectedAcceptable + optionID
        val next = it.copy(selectedAcceptable = acceptable)
        if (next.doesNotMatter) next.copy(selectedImportance = null) else next
    }

    fun selectImportance(importance: SimpaticoImportance) = _state.update { it.copy(selectedImportance = importance) }

    /** Skips without writing anything (the question stays as it was); only the position is saved. */
    fun skip() {
        if (_state.value.isSaving) return
        _state.update { it.copy(error = null) }
        viewModelScope.launch { advance() }
    }

    fun saveAndAdvance() {
        val s = _state.value
        val answerID = s.selectedAnswerID
        val uid = currentUid()
        if (!s.canAdvance || s.isSaving || answerID == null || uid == null) return

        val question = s.currentQuestion
        val answer = SimpaticoAnswer(
            answer = answerID,
            // The picker always holds the user's own answer; the union is belt-and-braces.
            // Written in the question's option order (iOS writes its Set in arbitrary order).
            acceptable = question.options.map { it.id }.filter { it in s.selectedAcceptable || it == answerID },
            importance = if (s.doesNotMatter) null else s.selectedImportance,
        )
        // Local state updates immediately so the count reacts without waiting for Firestore,
        // and the pickers are left as they are, so a failed save loses nothing.
        _state.update { it.copy(answers = it.answers + (question.id to answer), isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                store.saveAnswer(uid, question.id, answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save Simpatico answer ${question.id}", e)
                _state.update { it.copy(isSaving = false, error = SimpaticoError.SAVE) }
                return@launch
            }
            _state.update { it.copy(isSaving = false) }
            advance()
        }
    }

    /** Re-enters the flow at question 1 with every saved answer prefilled. */
    fun startEditing() = _state.update { it.copy(isEditing = true, currentIndex = 0).synced() }

    private suspend fun advance() {
        val uid = currentUid() ?: return
        if (_state.value.isLastQuestion) {
            try {
                val at = Instant.now()
                store.markComplete(uid, at)
                _state.update { it.copy(completedAt = at, isEditing = false) }
                positions.clear(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't mark Simpatico complete", e)
                _state.update { it.copy(error = SimpaticoError.FINISH) }
            }
            return
        }
        _state.update { it.copy(currentIndex = it.currentIndex + 1).synced() }
        positions.save(uid, _state.value.currentIndex)
    }

    /** iOS `syncPickers()`: the pickers show the current question's saved answer, or nothing. */
    private fun SimpaticoUiState.synced(): SimpaticoUiState {
        val existing = answers[currentQuestion.id]
        return copy(
            selectedAnswerID = existing?.answer,
            selectedAcceptable = existing?.acceptable?.toSet().orEmpty(),
            selectedImportance = existing?.importance?.takeIf { it != SimpaticoImportance.UNKNOWN },
        )
    }

    companion object {
        private const val TAG = "Simpatico"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AtxFriendsApp
                val session = app.container.session
                SimpaticoViewModel(
                    store = app.container.simpatico,
                    positions = SharedPrefsSimpaticoPositionStore(app),
                    currentUid = { (session.state.value as? SessionState.Ready)?.user?.uid },
                )
            }
        }
    }
}
