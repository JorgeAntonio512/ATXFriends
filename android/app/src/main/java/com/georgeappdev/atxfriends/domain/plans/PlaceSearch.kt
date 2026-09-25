package com.georgeappdev.atxfriends.domain.plans

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** One place-search result (iOS LocationSearchViewModel.Result). */
data class PlaceSuggestion(
    val id: String,
    val name: String,
    /** Street + city only, like iOS (`thoroughfare` + `locality`). */
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMiles: Double?,
)

/** iOS `LocationSearchViewModel.State`; the "Where?" field renders each one. */
sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Searching : PlaceSearchState
    data object NoMatches : PlaceSearchState
    data object Error : PlaceSearchState
    data class Results(val places: List<PlaceSuggestion>) : PlaceSearchState
}

/**
 * Where the "Where?" field's suggestions come from. Free-typed text is always a valid location;
 * a pick only adds a place name and coordinates for directions and calendars.
 *
 * Android ships [None] for now: place autocomplete needs a Google Places API key George hasn't
 * set up. A Places-backed implementation should mirror iOS: bias to a ~20 km box around the
 * user's stored coordinate (skip the (0,0) "unset" value), resolve up to 8 predictions, sort by
 * distance, keep 5, and debounce typing.
 */
fun interface PlaceSearch {
    /** Suggestion states for [query] near ([latitude], [longitude]); a blank query is Idle. */
    fun search(query: String, latitude: Double?, longitude: Double?): Flow<PlaceSearchState>

    companion object {
        /** No place search: the field is plain free text. */
        val None = PlaceSearch { _, _, _ -> flowOf(PlaceSearchState.Idle) }
    }
}
