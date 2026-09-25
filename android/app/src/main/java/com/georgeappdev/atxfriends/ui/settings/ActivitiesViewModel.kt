package com.georgeappdev.atxfriends.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.repository.ActivityCatalog
import com.georgeappdev.atxfriends.data.repository.CatalogActivity
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.georgeappdev.atxfriends.domain.profile.ActivitySelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivitiesUiState(
    val selected: List<ProfileActivity> = emptyList(),
    val catalog: List<CatalogActivity> = emptyList(),
    val catalogLoading: Boolean = true,
    val catalogFailed: Boolean = false,
    val search: String = "",
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isAdding: Boolean = false,
    val addFailed: Boolean = false,
    /** Bumped when a custom add from the sheet succeeds, so the sheet can close. */
    val customAdded: Int = 0,
) {
    val main: List<ProfileActivity> get() = selected.filter { it.isPrimary }
    val extras: List<ProfileActivity> get() = selected.filterNot { it.isPrimary }
    val isValid: Boolean get() = ActivitySelection.isValid(selected)
    val canAddMore: Boolean get() = ActivitySelection.canAddMore(selected)

    val available: List<CatalogActivity>
        get() = ActivitySelection.available(catalog, selected, search, { it.id }, { it.name })

    private val trimmedSearch: String get() = search.trim()
    private val hasExactMatch: Boolean get() = ActivitySelection.hasExactMatch(catalog, search) { it.name }

    /** "Add '…' — choose a category". */
    val showInlineAdd: Boolean get() = trimmedSearch.isNotEmpty() && !hasExactMatch
    val inlineAddName: String get() = trimmedSearch

    /** "Activity already exists" (the exact name is in the catalog but already picked). */
    val showAlreadyExists: Boolean get() = search.isNotEmpty() && available.isEmpty() && !showInlineAdd

    /** The generic "Add Custom Activity" button when not searching. */
    val showAddCustomButton: Boolean get() = search.isEmpty()

    /** Leaving is blocked until 3 Main / 3–10 total, and while a save is in flight. */
    val canLeave: Boolean get() = isValid && !isSaving && !isAdding
}

/**
 * iOS ActivitiesSettingsView + ProfileViewModel's activity methods. Every change autosaves,
 * but — unlike iOS, which writes whatever is selected — only a valid selection is written,
 * so a profile can never be saved with fewer than 3 Main activities.
 */
class ActivitiesViewModel(
    private val edits: ProfileEdits,
    private val catalogSource: ActivityCatalog,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivitiesUiState(selected = edits.profile?.activities.orEmpty()))
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val saver = AutoSaver<List<ProfileActivity>>(
        scope = viewModelScope,
        write = { list ->
            edits.save({ now -> UserWrites.activities(list, now) }) { p, now -> p.copy(activities = list, updatedAt = now) }
        },
        onStatus = { saving, failed -> _state.update { it.copy(isSaving = saving, saveFailed = failed) } },
    )

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        _state.update { it.copy(catalogLoading = true, catalogFailed = false) }
        viewModelScope.launch {
            try {
                val all = catalogSource.fetchAll()
                _state.update { it.copy(catalog = all, catalogLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(catalogLoading = false, catalogFailed = true) }
            }
        }
    }

    fun onSearchChange(text: String) = _state.update { it.copy(search = text, addFailed = false) }

    fun pick(activity: CatalogActivity) = change { ActivitySelection.select(it, activity.id, activity.name) }

    fun remove(id: String) = change { ActivitySelection.deselect(it, id) }

    fun makeMain(id: String) = change { ActivitySelection.toggleMain(it, id) }

    fun retrySave() = saver.retry()

    /**
     * iOS `addCustomActivity`: reuses an existing activity whose normalized name matches;
     * otherwise creates a new catalog entry under [category] and selects it.
     */
    fun addCustom(name: String, category: ActivityCategory, fromSheet: Boolean) {
        val cleaned = ActivitySelection.cleanedName(name)
        if (cleaned.isEmpty() || _state.value.isAdding) return
        val key = ActivitySelection.normalizedForComparison(cleaned)
        val existing = _state.value.catalog.firstOrNull { ActivitySelection.normalizedForComparison(it.name) == key }
        if (existing != null) {
            pick(existing)
            customAddSucceeded(fromSheet)
            return
        }
        _state.update { it.copy(isAdding = true, addFailed = false) }
        viewModelScope.launch {
            try {
                val created = catalogSource.addCustom(cleaned, category)
                _state.update { s -> s.copy(isAdding = false, catalog = (s.catalog + created).sortedBy { it.name }) }
                pick(created)
                customAddSucceeded(fromSheet)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isAdding = false, addFailed = true) }
            }
        }
    }

    fun dismissAddError() = _state.update { it.copy(addFailed = false) }

    private fun customAddSucceeded(fromSheet: Boolean) = _state.update {
        if (fromSheet) it.copy(customAdded = it.customAdded + 1) else it.copy(search = "")
    }

    private fun change(transform: (List<ProfileActivity>) -> List<ProfileActivity>) {
        val before = _state.value.selected
        val after = transform(before)
        if (after == before) return
        _state.update { it.copy(selected = after, saveFailed = false) }
        if (ActivitySelection.isValid(after)) saver.submit(after)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AtxFriendsApp).container
                ActivitiesViewModel(ProfileEdits(c.session, c.profiles), c.activities)
            }
        }
    }
}
