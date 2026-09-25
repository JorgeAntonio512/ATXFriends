package com.georgeappdev.atxfriends.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.georgeappdev.atxfriends.R
import kotlinx.serialization.Serializable

// Each tab owns a nested nav graph so later rounds can push screens inside it.
@Serializable data object MatchesGraph
@Serializable data object TodayGraph
@Serializable data object UpcomingGraph
@Serializable data object SimpaticoGraph
@Serializable data object MessagesGraph
@Serializable data object SettingsGraph

// Start destination of each tab's graph.
@Serializable data object MatchesHome
@Serializable data object TodayHome
@Serializable data object UpcomingHome
@Serializable data object SimpaticoHome
@Serializable data object MessagesHome
@Serializable data object SettingsHome

/** The six main tabs, in tab-bar order (parity spec §9, `MainTabView.swift`). */
enum class AppTab(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val graph: Any,
) {
    MATCHES(R.string.tab_matches, R.drawable.ic_tab_matches, R.drawable.ic_tab_matches_selected, MatchesGraph),
    TODAY(R.string.tab_today, R.drawable.ic_tab_today, R.drawable.ic_tab_today_selected, TodayGraph),
    // iOS uses the same symbol for both states on this tab.
    UPCOMING(R.string.tab_upcoming, R.drawable.ic_tab_upcoming, R.drawable.ic_tab_upcoming, UpcomingGraph),
    SIMPATICO(R.string.tab_simpatico, R.drawable.ic_tab_simpatico, R.drawable.ic_tab_simpatico_selected, SimpaticoGraph),
    MESSAGES(R.string.tab_messages, R.drawable.ic_tab_messages, R.drawable.ic_tab_messages_selected, MessagesGraph),
    SETTINGS(R.string.tab_settings, R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_selected, SettingsGraph),
}
