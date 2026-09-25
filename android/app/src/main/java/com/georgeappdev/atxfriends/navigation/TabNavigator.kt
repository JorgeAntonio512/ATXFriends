package com.georgeappdev.atxfriends.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-tab jumps — the Android side of iOS's `.navigateToUpcoming` and `.navigateToMatchThread`
 * notifications. MainScaffold provides it; screens read it with `LocalTabNavigator.current`.
 */
class TabNavigator(private val selectTab: (AppTab) -> Unit) {
    fun open(tab: AppTab) = selectTab(tab)
}

val LocalTabNavigator = staticCompositionLocalOf { TabNavigator {} }

/** A request to open one match's message thread (iOS `.navigateToMatchThread` userInfo). */
data class ThreadRequest(val matchID: String, val otherUserID: String, val otherUserName: String)

/**
 * Holds a thread-open request until the Messages tab picks it up. Today's "I'm in" posts one and
 * switches to Messages; the Messages screen should [consume] it and open that thread.
 */
class ThreadRequests {
    private val _pending = MutableStateFlow<ThreadRequest?>(null)
    val pending: StateFlow<ThreadRequest?> = _pending.asStateFlow()

    fun request(request: ThreadRequest) {
        _pending.value = request
    }

    /** Returns and clears the pending request, if any. */
    fun consume(): ThreadRequest? = _pending.value.also { _pending.value = null }
}
