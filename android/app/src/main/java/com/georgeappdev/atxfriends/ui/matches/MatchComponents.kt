package com.georgeappdev.atxfriends.ui.matches

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * A profile photo from its Firebase Storage download URL. Coil caches it in memory (so
 * scrolling never re-downloads) and on disk. [loading] shows while it downloads, [fallback]
 * quietly replaces it if it fails — iOS just logs failures to the console.
 */
@Composable
fun RemotePhoto(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable () -> Unit,
    fallback: @Composable () -> Unit = loading,
) {
    if (url == null) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { loading() },
        error = { fallback() },
    )
}

/** The iOS placeholder: tinted background with a `person.circle.fill` glyph, plus a spinner while loading. */
@Composable
fun PhotoPlaceholder(iconSize: Dp, showSpinner: Boolean, modifier: Modifier = Modifier) {
    val primary = AtxTheme.colors.appPrimary
    Box(modifier.fillMaxSize().background(primary.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(R.drawable.ic_person_circle),
                contentDescription = null,
                tint = primary.copy(alpha = 0.5f),
                modifier = Modifier.size(iconSize),
            )
            if (showSpinner) CircularProgressIndicator(color = primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

/** Wrapping burnt-orange chips (iOS FlowLayout of activity / time-slot pills). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(
    items: List<String>,
    fontSize: TextUnit,
    weight: FontWeight,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    cornerRadius: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    val primary = AtxTheme.colors.appPrimary
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items.forEach { item ->
            Text(
                item,
                style = atxText(fontSize, weight),
                color = primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(primary.copy(alpha = 0.15f))
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            )
        }
    }
}

/** Small icon + label row used for "Shared Interests", "Free at the same time", and distance. */
@Composable
fun IconLabel(
    @DrawableRes icon: Int,
    text: String,
    iconSize: Dp,
    iconTint: Color,
    textSize: TextUnit,
    textWeight: FontWeight,
    textColor: Color,
    modifier: Modifier = Modifier,
    spacing: Dp = 6.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
        Text(text, style = atxText(textSize, textWeight), color = textColor)
    }
}

/** Section header with title, subtitle, and the orange count badge. */
@Composable
fun SectionHeader(title: String, subtitle: String, count: Int, modifier: Modifier = Modifier) {
    val colors = AtxTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = atxText(22.sp, FontWeight.Bold), color = colors.primaryText)
            Text(subtitle, style = atxText(14.sp), color = colors.secondaryText)
        }
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                .clip(CircleShape)
                .background(colors.appPrimary)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), style = atxText(14.sp, FontWeight.Bold), color = Color.White)
        }
    }
}
