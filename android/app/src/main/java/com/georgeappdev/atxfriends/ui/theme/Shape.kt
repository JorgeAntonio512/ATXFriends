package com.georgeappdev.atxfriends.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii used across the iOS app, most common first (12, 16, 20, 14, 10, 24, 8). */
object AtxRadius {
    val xs = 8.dp
    val sm = 10.dp
    val md = 12.dp
    val mdPlus = 14.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

val AtxShapes = Shapes(
    extraSmall = RoundedCornerShape(AtxRadius.xs),
    small = RoundedCornerShape(AtxRadius.sm),
    medium = RoundedCornerShape(AtxRadius.md),
    large = RoundedCornerShape(AtxRadius.lg),
    extraLarge = RoundedCornerShape(AtxRadius.xl),
)
