package com.georgeappdev.atxfriends.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppTabTest {

    @Test
    fun tabsMatchIosOrderExactly() {
        assertEquals(
            listOf("MATCHES", "TODAY", "UPCOMING", "SIMPATICO", "MESSAGES", "SETTINGS"),
            AppTab.entries.map { it.name },
        )
    }

    @Test
    fun everyTabHasItsOwnGraph() {
        assertEquals(AppTab.entries.size, AppTab.entries.map { it.graph }.toSet().size)
    }
}
