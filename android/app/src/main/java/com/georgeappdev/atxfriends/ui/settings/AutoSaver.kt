package com.georgeappdev.atxfriends.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Autosave for screens that save on every tap (iOS `saveChangesImmediately`). Writes run one at
 * a time; taps made while a write is in flight are coalesced, so only the latest value is
 * written next. A failure stops the queue, keeps the latest value for [retry], and is reported
 * so the screen can show it.
 */
class AutoSaver<T : Any>(
    private val scope: CoroutineScope,
    private val write: suspend (T) -> Unit,
    private val onStatus: (saving: Boolean, failed: Boolean) -> Unit,
) {
    private var pending: T? = null
    private var unsaved: T? = null
    private var running = false

    val isSaving: Boolean get() = running

    fun submit(value: T) {
        pending = value
        if (!running) drain()
    }

    /** Writes the value whose save failed (or any newer one). */
    fun retry() {
        val value = pending ?: unsaved ?: return
        submit(value)
    }

    private fun drain() {
        running = true
        onStatus(true, false)
        scope.launch {
            var failed = false
            while (true) {
                val next = pending ?: break
                pending = null
                try {
                    write(next)
                    unsaved = null
                } catch (e: CancellationException) {
                    running = false
                    throw e
                } catch (_: Exception) {
                    unsaved = pending ?: next
                    pending = null
                    failed = true
                    break
                }
            }
            running = false
            onStatus(false, failed)
        }
    }
}
