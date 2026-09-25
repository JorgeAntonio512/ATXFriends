package com.georgeappdev.atxfriends.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.AtxFriendsApp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.repository.PhotoUploader
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.profile.PhotoRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PhotosUiState(
    /** Slot → URL, from `photoURLs` (blank or missing slots show the empty "+" tile). */
    val urls: List<String> = emptyList(),
    /** Freshly uploaded JPEGs, shown in place of their URL (the file name never changes). */
    val localJpegs: Map<Int, ByteArray> = emptyMap(),
    val uploading: Set<Int> = emptySet(),
    @field:StringRes val error: Int? = null,
) {
    fun url(index: Int): String? = urls.getOrNull(index)?.takeIf { it.isNotBlank() }
}

/**
 * iOS PhotosSettingsView + ProfileViewModel.updateSinglePhoto: tapping a slot replaces just that
 * photo — resize, upload to its fixed Storage file, then write `photoURLs` with only that slot
 * changed. Each slot is locked while it uploads; the Firestore writes run one at a time so two
 * quick replacements can't overwrite each other's URL.
 */
class PhotosViewModel(
    private val edits: ProfileEdits,
    private val uploader: PhotoUploader,
    private val encoder: PhotoEncoder?,
) : ViewModel() {

    private val _state = MutableStateFlow(PhotosUiState(urls = edits.profile?.photoURLs.orEmpty()))
    val state: StateFlow<PhotosUiState> = _state.asStateFlow()
    private val writeLock = Mutex()

    fun replace(index: Int, uri: Uri) {
        val encoder = encoder ?: return
        replace(index) { encoder.encode(uri) }
    }

    /** [encode] produces the JPEG to upload, or null if the picked image couldn't be read. */
    internal fun replace(index: Int, encode: suspend () -> ByteArray?) {
        val uid = edits.profile?.id ?: return
        if (index !in 0 until PhotoRules.SLOT_COUNT || index in _state.value.uploading) return
        _state.update { it.copy(uploading = it.uploading + index, error = null) }
        viewModelScope.launch {
            val error = try {
                val jpeg = encode()
                when {
                    jpeg == null -> R.string.photos_process_failed
                    !PhotoRules.isUnderSizeLimit(jpeg.size) -> R.string.photos_too_large
                    // Once the fixed Storage file is being replaced, finish and record the
                    // new URL even if the screen goes away mid-way.
                    else -> withContext(NonCancellable) {
                        val url = uploader.upload(uid, index, jpeg)
                        writeLock.withLock {
                            val urls = PhotoRules.replacing(edits.profile?.photoURLs.orEmpty(), index, url)
                            edits.save({ now -> UserWrites.photoURLs(urls, now) }) { p, now -> p.copy(photoURLs = urls, updatedAt = now) }
                            _state.update { it.copy(urls = urls, localJpegs = it.localJpegs + (index to jpeg)) }
                        }
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                R.string.photos_upload_failed
            }
            _state.update { it.copy(uploading = it.uploading - index, error = error ?: it.error) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AtxFriendsApp
                val c = app.container
                PhotosViewModel(ProfileEdits(c.session, c.profiles), c.photos, PhotoEncoder(app.contentResolver))
            }
        }
    }
}
