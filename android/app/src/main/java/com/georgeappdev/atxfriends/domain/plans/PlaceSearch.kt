package com.georgeappdev.atxfriends.domain.plans

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.cos

/** One place-search result (iOS LocationSearchViewModel.Result). */
data class PlaceSuggestion(
    val id: String,
    val name: String,
    /** Street + city only, like iOS (`thoroughfare` + `locality`). */
    val address: String?,
    /**
     * Null until the place is picked: Google's autocomplete predictions carry no coordinates, so
     * they're fetched once, for the chosen place only ([PlaceSearch.resolve]).
     */
    val latitude: Double?,
    val longitude: Double?,
    val distanceMiles: Double?,
)

data class PlaceCoordinate(val latitude: Double, val longitude: Double)

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
 * a pick only adds a place name and coordinates for directions.
 *
 * [None] (no Places API key in `local.properties`) leaves the field as plain free text. The
 * Google Places implementation lives in `data/places/GooglePlaceSearch.kt`.
 */
interface PlaceSearch {
    /** Suggestion states for [query] near ([latitude], [longitude]); a blank query is Idle. */
    fun search(query: String, latitude: Double?, longitude: Double?): Flow<PlaceSearchState>

    /** The picked place's coordinates, or null to keep it as free text only. */
    suspend fun resolve(place: PlaceSuggestion): PlaceCoordinate? = place.coordinate

    /** The composer closed: start the next one with a fresh search session. */
    fun endSession() = Unit

    companion object {
        /** No place search: the field is plain free text. */
        val None: PlaceSearch = object : PlaceSearch {
            override fun search(query: String, latitude: Double?, longitude: Double?) = flowOf(PlaceSearchState.Idle)
        }
    }
}

val PlaceSuggestion.coordinate: PlaceCoordinate?
    get() = if (latitude != null && longitude != null) PlaceCoordinate(latitude, longitude) else null

/** The iOS search rules, kept free of any SDK so they're unit-tested directly. */
object PlaceSearchRules {
    /** iOS shows at most 5 results. */
    const val MAX_RESULTS = 5

    /** iOS biases to a 20 km × 20 km box centered on the user. */
    const val BIAS_HALF_SIDE_KM = 10.0

    private const val METERS_PER_MILE = 1609.34
    private const val KM_PER_DEGREE_LATITUDE = 111.32

    /** The user's stored coordinate, or null for missing or the app's (0, 0) "unset" value. */
    fun origin(latitude: Double?, longitude: Double?): PlaceCoordinate? {
        if (latitude == null || longitude == null) return null
        if (latitude == 0.0 && longitude == 0.0) return null
        return PlaceCoordinate(latitude, longitude)
    }

    /** Southwest and northeast corners of the ~20 km bias box around [origin]. */
    fun biasBox(origin: PlaceCoordinate): Pair<PlaceCoordinate, PlaceCoordinate> {
        val dLat = BIAS_HALF_SIDE_KM / KM_PER_DEGREE_LATITUDE
        val dLng = BIAS_HALF_SIDE_KM / (KM_PER_DEGREE_LATITUDE * cos(Math.toRadians(origin.latitude)))
        return PlaceCoordinate(origin.latitude - dLat, origin.longitude - dLng) to
            PlaceCoordinate(origin.latitude + dLat, origin.longitude + dLng)
    }

    fun miles(meters: Int?): Double? = meters?.let { it / METERS_PER_MILE }

    /**
     * iOS re-sorts by distance itself rather than trusting the search's relevance order: nearest
     * first, places with no distance last (in their original order), then the first 5.
     */
    fun nearestFirst(places: List<PlaceSuggestion>): List<PlaceSuggestion> =
        places.sortedWith(compareBy(nullsLast()) { it.distanceMiles }).take(MAX_RESULTS)

    private val STATE = Regex("^[A-Z]{2}( \\d{5}(-\\d{4})?)?$")
    private val COUNTRIES = setOf("USA", "United States", "US")

    /**
     * iOS shows only street + city. Google's secondary text is "street, city, state, country",
     * so the trailing country and state are dropped: "W 6th St, Austin, TX, USA" → "W 6th St, Austin".
     */
    fun shortAddress(secondaryText: String?): String? {
        val parts = secondaryText.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (parts.isNotEmpty() && parts.last() in COUNTRIES) parts.removeAt(parts.lastIndex)
        if (parts.size > 1 && STATE.matches(parts.last())) parts.removeAt(parts.lastIndex)
        return parts.joinToString(", ").ifEmpty { null }
    }
}
