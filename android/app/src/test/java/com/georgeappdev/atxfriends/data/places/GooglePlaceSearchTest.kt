package com.georgeappdev.atxfriends.data.places

import com.georgeappdev.atxfriends.domain.plans.PlaceSearch
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchState
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.libraries.places.api.net.PlacesStatusCodes
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlaceSearchTest {

    private val google = object : PlaceSearch {
        override fun search(query: String, latitude: Double?, longitude: Double?) = flowOf<PlaceSearchState>()
    }

    @Test
    fun noKey_isPlainFreeText_andNeverStartsTheSdk() {
        var started = false
        for (key in listOf("", "   ")) {
            assertSame(PlaceSearch.None, GooglePlaceSearch.forKey(key) { started = true; google })
        }
        assertFalse(started)
    }

    @Test
    fun aKey_startsGoogleSearch_trimmed() {
        var usedKey = ""
        assertSame(google, GooglePlaceSearch.forKey(" test-places-key \n") { usedKey = it; google })
        assertEquals("test-places-key", usedKey)
    }

    @Test
    fun anSdkThatWontStart_fallsBackToFreeText() {
        assertSame(PlaceSearch.None, GooglePlaceSearch.forKey("test-places-key") { throw IllegalStateException("bad key") })
    }

    @Test
    fun badKeyAndQuota_turnSearchOff_butNetworkTroubleDoesNot() {
        assertTrue(GooglePlaceSearch.isPermanent(PlacesStatusCodes.REQUEST_DENIED))
        assertTrue(GooglePlaceSearch.isPermanent(PlacesStatusCodes.OVER_QUERY_LIMIT))
        assertFalse(GooglePlaceSearch.isPermanent(CommonStatusCodes.NETWORK_ERROR))
        assertFalse(GooglePlaceSearch.isPermanent(CommonStatusCodes.TIMEOUT))
        assertFalse("a non-API exception (e.g. IOException) is transient", GooglePlaceSearch.isPermanent(null))
    }
}
