package com.georgeappdev.atxfriends.push

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tapped push waiting to be opened (iOS `NotificationManager.pendingThreadRoute`). Set by
 * MainActivity from the launch or new intent; consumed by the signed-in tab shell, which may not
 * exist yet on a cold start — so it waits here until it does.
 */
class PushRoutes {
    private val _pending = MutableStateFlow<PushRoute?>(null)
    val pending: StateFlow<PushRoute?> = _pending.asStateFlow()

    fun post(route: PushRoute) {
        Log.i(TAG, "push: tap → open thread ${route.matchID} (fallback to Matches: ${route.fallbackToMatchesTab})")
        _pending.value = route
    }

    /** Returns and clears the pending route, if any. */
    fun consume(): PushRoute? = _pending.value.also { _pending.value = null }

    fun clear() {
        _pending.value = null
    }
}

/**
 * The conversation currently on screen (iOS `currentlyOpenMatchID`, set and cleared by
 * MessageThreadView), so a foreground push about it isn't shown.
 */
object OpenThreadTracker {
    @Volatile
    var openMatchID: String? = null
        private set

    fun opened(matchID: String) {
        openMatchID = matchID
    }

    fun closed(matchID: String) {
        if (openMatchID == matchID) openMatchID = null
    }
}
