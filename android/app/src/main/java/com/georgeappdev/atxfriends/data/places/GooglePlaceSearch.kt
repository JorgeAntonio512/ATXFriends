package com.georgeappdev.atxfriends.data.places

import android.content.Context
import android.util.Log
import com.georgeappdev.atxfriends.domain.plans.PlaceCoordinate
import com.georgeappdev.atxfriends.domain.plans.PlaceSearch
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchRules
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchState
import com.georgeappdev.atxfriends.domain.plans.PlaceSuggestion
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * "Where?" suggestions from Google Places API (New), the Android stand-in for iOS's MapKit search
 * (LocationSearchViewModel). Mirrors iOS: biased to a ~20 km box around the user's *stored*
 * coordinate (never a fresh location fix), re-sorted nearest first on the device, at most 5.
 *
 * Billing (per Google's session-token guidance): typing is debounced, every autocomplete call
 * in one composer shares a session token, and picking a place ends the session with a Place
 * Details call that asks only for `LOCATION` (an Essentials field — the name comes from the
 * prediction, since `DISPLAY_NAME` would bill at the Pro rate).
 *
 * Every failure is quiet: a transient one (no network) shows iOS's inline "Couldn't search right
 * now" row, and a permanent one (bad key, API not enabled, quota exceeded) turns search off for
 * the rest of the app run, leaving plain free text. Nothing ever pops a dialog.
 */
class GooglePlaceSearch(private val client: PlacesClient) : PlaceSearch {

    private var session: AutocompleteSessionToken? = null

    @Volatile private var disabled = false

    override fun search(query: String, latitude: Double?, longitude: Double?): Flow<PlaceSearchState> = flow {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH || disabled) {
            emit(PlaceSearchState.Idle)
            return@flow
        }
        emit(PlaceSearchState.Searching)
        // The composer cancels this flow on the next keystroke, so only a pause gets this far.
        delay(DEBOUNCE_MS)

        val origin = PlaceSearchRules.origin(latitude, longitude)
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(q)
            .setSessionToken(sessionToken())
            .apply {
                if (origin != null) {
                    val (sw, ne) = PlaceSearchRules.biasBox(origin)
                    setLocationBias(RectangularBounds.newInstance(LatLng(sw.latitude, sw.longitude), LatLng(ne.latitude, ne.longitude)))
                    setOrigin(LatLng(origin.latitude, origin.longitude))
                }
            }
        val cancel = CancellationTokenSource()
        val predictions = try {
            client.findAutocompletePredictions(request.setCancellationToken(cancel.token).build())
                .await(cancel).autocompletePredictions
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("autocomplete", e)
            emit(if (disabled) PlaceSearchState.Idle else PlaceSearchState.Error)
            return@flow
        }
        val places = PlaceSearchRules.nearestFirst(predictions.map(::suggestion))
        Log.d(TAG, "places: ${predictions.size} predictions → showing ${places.size} (biased=${origin != null})")
        emit(if (places.isEmpty()) PlaceSearchState.NoMatches else PlaceSearchState.Results(places))
    }

    /** Ends the billing session: one Place Details call, for the coordinates only. */
    override suspend fun resolve(place: PlaceSuggestion): PlaceCoordinate? {
        val token = session
        session = null
        val request = FetchPlaceRequest.builder(place.id, listOf(Place.Field.LOCATION))
            .apply { if (token != null) setSessionToken(token) }
            .build()
        return try {
            val location = client.fetchPlace(request).await().place.location
            Log.d(TAG, "places: resolved a pick, hasCoordinates=${location != null}")
            location?.let { PlaceCoordinate(it.latitude, it.longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("place details", e)
            null
        }
    }

    override fun endSession() {
        session = null
    }

    private fun sessionToken(): AutocompleteSessionToken =
        session ?: AutocompleteSessionToken.newInstance().also { session = it }

    private fun suggestion(p: AutocompletePrediction) = PlaceSuggestion(
        id = p.placeId,
        name = p.getPrimaryText(null).toString(),
        address = PlaceSearchRules.shortAddress(p.getSecondaryText(null).toString()),
        latitude = null,
        longitude = null,
        distanceMiles = PlaceSearchRules.miles(p.distanceMeters),
    )

    private fun onFailure(call: String, e: Exception) {
        val status = (e as? ApiException)?.statusCode
        if (isPermanent(status)) {
            if (!disabled) Log.w(TAG, "places: $call failed with status $status; place search is off until the app restarts (key, API enablement, or quota)", e)
            disabled = true
        } else {
            Log.w(TAG, "places: $call failed (status $status); falling back to free text", e)
        }
    }

    companion object {
        private const val TAG = "ATXF"
        const val DEBOUNCE_MS = 300L

        /** One letter matches half of Austin; waiting for two keeps each billed call useful. */
        const val MIN_QUERY_LENGTH = 2

        /** Failures a retry can't fix: a bad or restricted key, the API not enabled, or the quota cap. */
        fun isPermanent(statusCode: Int?): Boolean = statusCode in setOf(
            PlacesStatusCodes.REQUEST_DENIED,
            PlacesStatusCodes.OVER_QUERY_LIMIT,
            CommonStatusCodes.API_NOT_CONNECTED,
            CommonStatusCodes.DEVELOPER_ERROR,
        )

        /**
         * The app's place search: Google Places when a key is configured, otherwise [PlaceSearch.None]
         * (plain free text). A key the SDK rejects at startup also falls back to free text.
         */
        fun create(context: Context, apiKey: String): PlaceSearch = forKey(apiKey) { key ->
            if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
            GooglePlaceSearch(Places.createClient(context.applicationContext))
        }

        internal fun forKey(apiKey: String, build: (String) -> PlaceSearch): PlaceSearch {
            if (apiKey.isBlank()) {
                Log.i(TAG, "places: no PLACES_API_KEY in local.properties; Where? is free text only")
                return PlaceSearch.None
            }
            return try {
                build(apiKey.trim()).also { Log.i(TAG, "places: Google place search on") }
            } catch (e: Exception) {
                Log.w(TAG, "places: couldn't start the Places SDK; Where? is free text only", e)
                PlaceSearch.None
            }
        }
    }
}
