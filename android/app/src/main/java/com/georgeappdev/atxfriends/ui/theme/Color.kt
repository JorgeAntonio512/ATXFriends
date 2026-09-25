package com.georgeappdev.atxfriends.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Fixed brand colors — identical in Light and Dark (AppColors.swift).
val BurntOrange = Color(0xFFBF5700)
val DeepNavy = Color(0xFF1B2A47)

/**
 * Theme-adaptive tokens, mirroring the iOS asset-catalog colorsets one-for-one
 * (parity spec §9). Names match the iOS token names so screens can be ported literally.
 */
@Immutable
data class AtxColors(
    val appBackground: Color,
    val border: Color,
    val cardBackground: Color,
    val danger: Color,
    val positiveCardBg: Color,
    val primaryText: Color,
    val promptCreamBg: Color,
    val secondaryText: Color,
    val textBody: Color,
    val textFaint: Color,
    val textHeavy: Color,
    val textMedium: Color,
    val textMuted: Color,
    val textStrong: Color,
    val textSubtle: Color,
    val textTertiary: Color,
    val appPrimary: Color = BurntOrange,
    val appNavy: Color = DeepNavy,
)

val LightAtxColors = AtxColors(
    appBackground = Color(0xFFFAF6EE),
    border = Color(0xFFD9D9D9),
    cardBackground = Color(0xFFFFFFFF),
    danger = Color(0xFFD97366),
    positiveCardBg = Color(0xFFEDF7ED),
    primaryText = Color(0xFF1B2A47),
    promptCreamBg = Color(0xFFFCF2DB),
    secondaryText = Color(0xFF808080),
    textBody = Color(0xFF666666),
    textFaint = Color(0xFFA6A6A6),
    textHeavy = Color(0xFF404040),
    textMedium = Color(0xFF737373),
    textMuted = Color(0xFF999999),
    textStrong = Color(0xFF595959),
    textSubtle = Color(0xFFB3B3B3),
    textTertiary = Color(0xFF8C8C8C),
)

val DarkAtxColors = AtxColors(
    appBackground = Color(0xFF12151C),
    border = Color(0xFF3A3F4A),
    cardBackground = Color(0xFF1E232C),
    danger = Color(0xFFE2857A),
    positiveCardBg = Color(0xFF18221A),
    primaryText = Color(0xFFF1EDE3),
    promptCreamBg = Color(0xFF262014),
    secondaryText = Color(0xFFA8A29B),
    textBody = Color(0xFFB9B9B9),
    textFaint = Color(0xFF838383),
    textHeavy = Color(0xFFD9D9D9),
    textMedium = Color(0xFFAEAEAE),
    textMuted = Color(0xFF8E8E8E),
    textStrong = Color(0xFFC4C4C4),
    textSubtle = Color(0xFF787878),
    textTertiary = Color(0xFF999999),
)
