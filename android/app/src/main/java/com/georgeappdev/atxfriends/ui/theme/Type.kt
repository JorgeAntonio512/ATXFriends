package com.georgeappdev.atxfriends.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// iOS uses SF Pro Rounded everywhere (design: .rounded). Android has no system rounded
// face, so this uses the platform default (Roboto) until a bundled font is chosen.
private val AppFontFamily = FontFamily.Default

// iOS has no type scale; sizes are literal per call site. The most common iOS combos
// (15 semibold, 14 semibold, 16 medium) are mapped onto the M3 slots below; the rest
// keep M3 defaults with the app font applied.
private val Base = Typography()

val AtxTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = Base.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = Base.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = Base.headlineLarge.copy(fontFamily = AppFontFamily),
    headlineMedium = Base.headlineMedium.copy(fontFamily = AppFontFamily),
    headlineSmall = Base.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = Base.titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    // iOS tab-bar label: 10pt medium.
    labelSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp),
)
