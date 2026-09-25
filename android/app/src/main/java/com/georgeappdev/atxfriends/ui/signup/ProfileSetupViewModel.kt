package com.georgeappdev.atxfriends.ui.signup

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.ActivityCatalog
import com.georgeappdev.atxfriends.data.repository.CatalogActivity
import com.georgeappdev.atxfriends.data.repository.PhotoUploader
import com.georgeappdev.atxfriends.data.repository.ProfileWriter
import com.georgeappdev.atxfriends.data.repository.SignupWrites
import com.georgeappdev.atxfriends.data.repository.withWriteTimeout
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.georgeappdev.atxfriends.domain.profile.ActivitySelection
import com.georgeappdev.atxfriends.domain.profile.AvailabilityRules
import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import com.georgeappdev.atxfriends.domain.profile.SetupProblem
import com.georgeappdev.atxfriends.domain.profile.SetupRules
import com.georgeappdev.atxfriends.domain.profile.SetupStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

data class ProfileSetupUiState(
    val step: SetupStep = SetupStep.NAME,
    val displayName: String = "",
    val bio: String = "",
    /** NameInputView `showError`: set once they try to go on with an invalid name. */
    val showNameError: Boolean = false,
    /** The picked photos, already resized and JPEG-encoded, in pick order. */
    val photos: List<ByteArray> = emptyList(),
    val photosLoading: Boolean = false,
    @field:StringRes val photoError: Int? = null,
    val activities: List<ProfileActivity> = emptyList(),
    val catalog: List<CatalogActivity> = emptyList(),
    val catalogLoading: Boolean = true,
    val catalogFailed: Boolean = false,
    val search: String = "",
    val isAddingActivity: Boolean = false,
    val addActivityFailed: Boolean = false,
    /** "You can only select up to 10 activities." after tapping an 11th. */
    val activityLimitHit: Boolean = false,
    val combos: List<DaySlotCombo> = emptyList(),
    val isSaving: Boolean = false,
    @field:StringRes val saveError: Int? = null,
) {
    val canContinue: Boolean get() = SetupRules.canContinue(step, displayName, photos.size, activities, combos)
    val mainActivities: List<ProfileActivity> get() = activities.filter { it.isPrimary }
    val extraActivities: List<ProfileActivity> get() = activities.filterNot { it.isPrimary }

    /** ActivityPickerView.filteredActivities: nothing until they type, then every name match. */
    val searchResults: List<CatalogActivity>
        get() = if (search.isEmpty()) emptyList() else catalog.filter { it.name.contains(search, ignoreCase = true) }

    /** ActivityPickerView.shouldShowAddOption. */
    val showAddOption: Boolean
        get() = search.trim().isNotEmpty() && !ActivitySelection.hasExactMatch(catalog, search) { it.name } && searchResults.isEmpty()

    fun isSelected(id: String) = activities.any { it.id == id }
    fun isSelected(day: DayOfWeek, slot: TimeSlot) = AvailabilityRules.isSelected(combos, day, slot)
}

/**
 * Port of iOS ProfileSetupFlowView + ProfileViewModel: name → photos → activities → time slots
 * → "You're All Set!". Nothing is written until "Enter ATX Friends", which uploads the three
 * photos and then writes the profile once, `isProfileComplete: true` included. (iOS marks the
 * profile complete first and uploads photos afterwards in the background, so a failed or killed
 * upload leaves a "complete" profile with no photos; Android doesn't.) Every failure keeps what
 * was entered and can be retried.
 */
class ProfileSetupViewModel(
    private val profile: UserProfile,
    private val catalogSource: ActivityCatalog,
    private val uploader: PhotoUploader,
    private val writer: ProfileWriter,
    /** Profile saved: route to the six tabs (SessionManager.retry). */
    private val onFinished: () -> Unit,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileSetupUiState(
            // loadUserProfile pre-fills from the doc (a Google account's name, say).
            displayName = profile.displayName,
            bio = SetupRules.cappedBio(profile.bio),
            activities = profile.activities.take(ActivitySelection.MAX_TOTAL),
            combos = profile.daySlotCombos.filter { it.isKnown },
        )
    )
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        loadCatalog()
    }

    // Navigation

    fun next() {
        val s = _state.value
        if (s.step == SetupStep.NAME && !s.canContinue) {
            _state.update { it.copy(showNameError = true) }
            return
        }
        if (!s.canContinue) return
        s.step.next?.let { next -> _state.update { it.copy(step = next) } }
    }

    /** iOS has no working Back on the first step. */
    fun back() {
        val s = _state.value
        if (s.isSaving || s.step == SetupStep.NAME) return
        s.step.previous?.let { prev -> _state.update { it.copy(step = prev) } }
    }

    // Name

    fun onNameChange(value: String) = _state.update { it.copy(displayName = value) }
    fun onBioChange(value: String) = _state.update { it.copy(bio = SetupRules.cappedBio(value)) }

    // Photos

    fun photosNeeded(): Int = SetupRules.photosStillNeeded(_state.value.photos.size)

    /**
     * PhotoPickerView.loadImages: processes the picks one at a time so they keep their order,
     * adding each as it's ready, never past three.
     */
    fun addPhotos(count: Int, encode: suspend (index: Int) -> ByteArray?) {
        if (count <= 0 || _state.value.photosLoading) return
        _state.update { it.copy(photosLoading = true, photoError = null) }
        viewModelScope.launch {
            var error: Int? = null
            for (i in 0 until count) {
                if (_state.value.photos.size >= PhotoRules.SLOT_COUNT) break
                val jpeg = try {
                    encode(i)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                when {
                    jpeg == null -> error = R.string.photos_process_failed
                    !PhotoRules.isUnderSizeLimit(jpeg.size) -> error = R.string.photos_too_large
                    else -> _state.update { it.copy(photos = (it.photos + jpeg).take(PhotoRules.SLOT_COUNT)) }
                }
            }
            _state.update { it.copy(photosLoading = false, photoError = error) }
        }
    }

    fun removePhoto(index: Int) = _state.update { s ->
        if (index !in s.photos.indices) s else s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }, photoError = null)
    }

    // Activities

    fun loadCatalog() {
        _state.update { it.copy(catalogLoading = true, catalogFailed = false) }
        viewModelScope.launch {
            try {
                val all = catalogSource.fetchAll()
                _state.update { it.copy(catalog = all, catalogLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "setup: activities failed to load", e)
                _state.update { it.copy(catalogLoading = false, catalogFailed = true) }
            }
        }
    }

    fun dismissCatalogError() = _state.update { it.copy(catalogFailed = false) }

    fun onSearchChange(text: String) = _state.update { it.copy(search = text, addActivityFailed = false, activityLimitHit = false) }

    /** ActivityCard tap: toggles. */
    fun toggleActivity(activity: CatalogActivity) {
        val s = _state.value
        if (s.isSelected(activity.id)) {
            _state.update { it.copy(activities = ActivitySelection.deselect(it.activities, activity.id), activityLimitHit = false) }
        } else select(activity)
    }

    fun removeActivity(id: String) =
        _state.update { it.copy(activities = ActivitySelection.deselect(it.activities, id), activityLimitHit = false) }

    fun makeMain(id: String) = _state.update { it.copy(activities = ActivitySelection.toggleMain(it.activities, id)) }

    private fun select(activity: CatalogActivity) {
        if (!ActivitySelection.canAddMore(_state.value.activities)) {
            _state.update { it.copy(activityLimitHit = true) }
            return
        }
        _state.update { it.copy(activities = ActivitySelection.select(it.activities, activity.id, activity.name), activityLimitHit = false) }
    }

    /**
     * ProfileViewModel.addCustomActivity from the search field: reuse an existing activity whose
     * normalized name matches; otherwise create it in [category] and select it.
     */
    fun addCustomActivity(category: ActivityCategory) {
        val s = _state.value
        val cleaned = ActivitySelection.cleanedName(s.search)
        if (cleaned.isEmpty() || s.isAddingActivity) return
        val key = ActivitySelection.normalizedForComparison(cleaned)
        s.catalog.firstOrNull { ActivitySelection.normalizedForComparison(it.name) == key }?.let { existing ->
            if (!s.isSelected(existing.id)) select(existing)
            _state.update { it.copy(search = "") }
            return
        }
        _state.update { it.copy(isAddingActivity = true, addActivityFailed = false) }
        viewModelScope.launch {
            try {
                val created = catalogSource.addCustom(cleaned, category)
                _state.update { it.copy(isAddingActivity = false, catalog = (it.catalog + created).sortedBy { c -> c.name }, search = "") }
                select(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "setup: adding a custom activity failed", e)
                _state.update { it.copy(isAddingActivity = false, addActivityFailed = true) }
            }
        }
    }

    // Time slots

    fun toggleSlot(day: DayOfWeek, slot: TimeSlot) = _state.update { it.copy(combos = AvailabilityRules.toggle(it.combos, day, slot)) }
    fun removeSlot(combo: DaySlotCombo) = _state.update { it.copy(combos = it.combos - combo) }

    // Save

    /** "Enter ATX Friends". */
    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val problem = SetupRules.problem(s.displayName, s.photos.size, s.activities, s.combos, profile.latitude, profile.longitude)
        if (problem != null) {
            _state.update { it.copy(saveError = problem.message) }
            return
        }
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            // Finish the uploads and the write even if the screen goes away mid-save.
            val error = withContext(NonCancellable) {
                val urls = upload(s)
                if (urls == null) R.string.setup_save_error_photos else write(s, urls)
            }
            _state.update { it.copy(isSaving = false, saveError = error) }
            if (error == null) onFinished()
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    /** All three photos, in parallel, to their fixed Storage files. Null if any fails. */
    private suspend fun upload(s: ProfileSetupUiState): List<String>? = try {
        coroutineScope {
            s.photos.mapIndexed { index, jpeg -> async { withWriteTimeout(UPLOAD_TIMEOUT_MS) { uploader.upload(profile.id, index, jpeg) } } }.awaitAll()
        }
    } catch (e: Exception) {
        Log.w(TAG, "setup: photo upload failed", e)
        null
    }

    /** The one profile write. Returns an error string, or null when saved. */
    private suspend fun write(s: ProfileSetupUiState, urls: List<String>): Int? = try {
        withWriteTimeout { writer.update(profile.id, SignupWrites.completeProfile(s.displayName, s.bio, urls, s.activities, s.combos, clock())) }
        Log.i(TAG, "[Onboarding] path=setup step=mainTab gate=passed — users/${profile.id} complete")
        null
    } catch (e: Exception) {
        Log.w(TAG, "setup: profile write failed", e)
        R.string.setup_save_error_generic
    }

    private val SetupProblem.message: Int
        get() = when (this) {
            SetupProblem.NAME -> R.string.setup_invalid_name
            SetupProblem.PHOTOS -> R.string.setup_invalid_photos
            SetupProblem.ACTIVITY_COUNT -> R.string.setup_invalid_activity_count
            SetupProblem.MAIN_COUNT -> R.string.setup_invalid_main_count
            SetupProblem.TIME_SLOTS -> R.string.setup_invalid_times
            SetupProblem.LOCATION -> R.string.setup_invalid_location
        }

    private companion object {
        const val TAG = "ATXF"
        /** A photo is up to ~1 MB; allow a slow connection before calling it failed. */
        const val UPLOAD_TIMEOUT_MS = 90_000L
    }
}
