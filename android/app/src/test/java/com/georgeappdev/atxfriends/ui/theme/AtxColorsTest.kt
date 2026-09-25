package com.georgeappdev.atxfriends.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the palette against drift from parity spec §9. */
class AtxColorsTest {

    private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

    @Test
    fun brandColorsAreFixed() {
        assertEquals("#BF5700", hex(LightAtxColors.appPrimary))
        assertEquals("#BF5700", hex(DarkAtxColors.appPrimary))
        assertEquals("#1B2A47", hex(LightAtxColors.appNavy))
        assertEquals("#1B2A47", hex(DarkAtxColors.appNavy))
    }

    @Test
    fun keyAdaptiveTokensMatchSpec() {
        assertEquals("#FAF6EE", hex(LightAtxColors.appBackground))
        assertEquals("#12151C", hex(DarkAtxColors.appBackground))
        assertEquals("#1B2A47", hex(LightAtxColors.primaryText))
        assertEquals("#F1EDE3", hex(DarkAtxColors.primaryText))
        assertEquals("#FFFFFF", hex(LightAtxColors.cardBackground))
        assertEquals("#1E232C", hex(DarkAtxColors.cardBackground))
    }
}
