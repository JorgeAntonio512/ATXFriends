package com.georgeappdev.atxfriends.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchState
import com.georgeappdev.atxfriends.domain.plans.PlaceSuggestion
import com.georgeappdev.atxfriends.ui.components.atxText
import com.georgeappdev.atxfriends.ui.theme.AtxTheme

/**
 * Port of iOS PlanLocationField, the shared, required "Where?" field. Free-typed text is a
 * valid location; a place pick (when a [PlaceSearchState] source exists) only adds a name and
 * coordinates. The red hint appears only after the field was focused and then left empty, and
 * clears the moment there's text. Search status rows show only while the field has focus.
 */
@Composable
fun PlanLocationField(
    where: WhereState,
    searchState: PlaceSearchState,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onLeft: () -> Unit,
    onPlacePicked: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AtxTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    val placeholder = stringResource(R.string.where_placeholder)
    val label = stringResource(R.string.where_label)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ComposerLabel(R.drawable.ic_location, label)

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardBackground.copy(alpha = 0.85f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val textStyle = atxText(16.sp).copy(color = colors.primaryText)
            BasicTextField(
                value = where.text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.appPrimary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (isFocused && !state.isFocused) onLeft()
                        isFocused = state.isFocused
                    }
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box {
                        if (where.text.isEmpty()) Text(placeholder, style = textStyle.copy(color = colors.textSubtle))
                        inner()
                    }
                },
            )
            if (where.text.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_close_circle),
                    contentDescription = stringResource(R.string.where_clear),
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClear),
                )
            }
        }

        if (isFocused) {
            when (searchState) {
                PlaceSearchState.Idle -> Unit
                PlaceSearchState.Searching -> StatusRow(stringResource(R.string.where_searching), showsProgress = true)
                PlaceSearchState.NoMatches -> StatusRow(stringResource(R.string.where_no_matches), icon = R.drawable.ic_error)
                PlaceSearchState.Error -> StatusRow(stringResource(R.string.where_search_error), icon = R.drawable.ic_error)
                is PlaceSearchState.Results -> ResultsList(searchState.places, onPlacePicked)
            }
        }

        if (where.showMissingHint) {
            Text(
                stringResource(R.string.where_missing_hint),
                style = atxText(13.sp),
                color = colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, icon: Int? = null, showsProgress: Boolean = false) {
    val colors = AtxTheme.colors
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showsProgress) {
            CircularProgressIndicator(color = colors.appPrimary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
        }
        Text(label, style = atxText(13.sp), color = colors.textTertiary)
    }
}

@Composable
private fun ResultsList(places: List<PlaceSuggestion>, onPick: (PlaceSuggestion) -> Unit) {
    val colors = AtxTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.cardBackground.copy(alpha = 0.9f))) {
        places.forEachIndexed { index, place ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPick(place) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_location), contentDescription = null, tint = colors.appPrimary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(place.name, style = atxText(15.sp, FontWeight.Medium), color = colors.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    place.address?.let {
                        Text(it, style = atxText(12.sp), color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                place.distanceMiles?.let { miles ->
                    Text(
                        stringResource(if (miles < 1) R.string.where_distance_tenths else R.string.where_distance_whole, miles),
                        style = atxText(12.sp, FontWeight.Medium),
                        color = colors.textTertiary,
                    )
                }
            }
            if (index != places.lastIndex) HorizontalDivider(Modifier.padding(start = 14.dp))
        }
    }
}
