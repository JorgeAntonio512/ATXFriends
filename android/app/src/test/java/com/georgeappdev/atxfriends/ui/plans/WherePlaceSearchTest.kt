package com.georgeappdev.atxfriends.ui.plans

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.firestore.NewDocument
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.plans.PlaceCoordinate
import com.georgeappdev.atxfriends.domain.plans.PlaceSearch
import com.georgeappdev.atxfriends.domain.plans.PlaceSearchState
import com.georgeappdev.atxfriends.domain.plans.PlaceSuggestion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId

/** The "Where?" field wired to a place search: bias, pick, edit-after-pick, fallback, saved shape. */
@OptIn(ExperimentalCoroutinesApi::class)
class WherePlaceSearchTest {

    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 9, 25, 16, 20).atZone(zone).toInstant()

    /** Stored signup coordinates (already coarse-snapped, as every user doc is). */
    private val me: UserProfile = UserProfile.fromFirestore("me", TestDocs.user())!!.copy(id = "me", latitude = 30.27, longitude = -97.74)

    private val alamo = PlaceSuggestion("alamo-lamar", "Alamo Drafthouse South Lamar", "1120 S Lamar Blvd, Austin", null, null, 1.8)
    private val alamoSpot = PlaceCoordinate(30.2567, -97.7633)

    /** A fake Places source that records what it was asked. */
    private inner class FakeSearch : PlaceSearch {
        val queries = mutableListOf<Triple<String, Double?, Double?>>()
        var resolveResult: suspend () -> PlaceCoordinate? = { alamoSpot }
        var sessionsEnded = 0

        override fun search(query: String, latitude: Double?, longitude: Double?): Flow<PlaceSearchState> {
            queries += Triple(query, latitude, longitude)
            return flowOf(if (query.isBlank()) PlaceSearchState.Idle else PlaceSearchState.Results(listOf(alamo)))
        }

        override suspend fun resolve(place: PlaceSuggestion) = resolveResult()
        override fun endSession() { sessionsEnded++ }
    }

    private val proposed = mutableListOf<NewDocument>()

    private fun vm(search: PlaceSearch) = PlanComposerViewModel(
        request = ComposerRequest(ComposerMode.Proposal("bea_me", "bea")),
        myID = "me",
        fetchActivityNames = { emptyList() },
        fetchUser = { id -> if (id == "me") UserDoc.Found(me) else UserDoc.Missing },
        fetchMatches = { emptyList() },
        propose = { plan, _ -> proposed += plan; "NEW-PLAN" },
        postTodayPlan = { "NEW-TODAY" },
        createGroupPlan = { "NEW-GROUP" },
        placeSearch = search,
        clock = { now },
        zone = { zone },
    ).apply { onActivityChange("Movies") }

    @Test
    fun search_isBiasedToMyStoredCoordinates() {
        val search = FakeSearch()
        val vm = vm(search)
        vm.onWhereChange("Alamo Drafthouse")
        assertEquals(Triple("Alamo Drafthouse", 30.27, -97.74), search.queries.last())
        assertEquals(PlaceSearchState.Results(listOf(alamo)), vm.state.value.placeSearch)
    }

    @Test
    fun picking_fillsTheText_andStoresNameAndCoordinates() {
        val vm = vm(FakeSearch())
        vm.onWhereChange("Alamo Drafthouse")
        vm.onPlacePicked(alamo)
        with(vm.state.value) {
            assertEquals("Alamo Drafthouse South Lamar", where.text)
            assertEquals("Alamo Drafthouse South Lamar", where.locationName)
            assertEquals(alamoSpot.latitude, where.latitude!!, 0.0)
            assertEquals(alamoSpot.longitude, where.longitude!!, 0.0)
            assertEquals("results close after a pick", PlaceSearchState.Idle, placeSearch)
        }
    }

    @Test
    fun editingAfterAPick_clearsTheCoordinates() {
        val vm = vm(FakeSearch())
        vm.onPlacePicked(alamo)
        vm.onWhereChange("Alamo Drafthouse South Lamar, back patio")
        with(vm.state.value.where) {
            assertEquals("Alamo Drafthouse South Lamar, back patio", text)
            assertNull(locationName)
            assertNull(latitude)
            assertNull(longitude)
        }
    }

    @Test
    fun editingWhileTheLookupIsRunning_throwsAwayTheLateCoordinates() {
        val search = FakeSearch()
        val lookup = CompletableDeferred<PlaceCoordinate?>()
        search.resolveResult = { lookup.await() }
        val vm = vm(search)
        vm.onPlacePicked(alamo)
        assertNull("name and coordinates are saved together, not before", vm.state.value.where.locationName)
        vm.onWhereChange("my backyard")
        lookup.complete(alamoSpot)
        with(vm.state.value.where) {
            assertEquals("my backyard", text)
            assertNull(locationName)
            assertNull(latitude)
        }
    }

    @Test
    fun aFailedLookup_leavesThePickAsFreeText() {
        val search = FakeSearch()
        search.resolveResult = { throw IOException("offline") }
        val vm = vm(search)
        vm.onPlacePicked(alamo)
        with(vm.state.value) {
            assertEquals("Alamo Drafthouse South Lamar", where.text)
            assertNull(where.locationName)
            assertNull(where.latitude)
            assertTrue(canSubmit)
        }
    }

    @Test
    fun noKey_isPlainFreeText_neverSearches_andStillSends() {
        var fetchedMe = false
        val vm = PlanComposerViewModel(
            request = ComposerRequest(ComposerMode.Proposal("bea_me", "bea")),
            myID = "me",
            fetchActivityNames = { emptyList() },
            fetchUser = { fetchedMe = true; UserDoc.Found(me) },
            fetchMatches = { emptyList() },
            propose = { plan, _ -> proposed += plan; "NEW-PLAN" },
            postTodayPlan = { "" },
            createGroupPlan = { "" },
            placeSearch = PlaceSearch.None,
            clock = { now },
            zone = { zone },
        )
        vm.onActivityChange("Movies")
        vm.onWhereChange("my backyard")
        assertEquals(PlaceSearchState.Idle, vm.state.value.placeSearch)
        assertFalse("no key means no profile fetch for search bias", fetchedMe)
        vm.submit()
        assertEquals(mapOf("location" to "my backyard"), placeFields(proposed.single()))
    }

    // ── What's saved: iOS's four fields, same names and types ──────────────────────────────

    private fun placeFields(doc: NewDocument) = doc.fields.filterKeys { it.startsWith("location") }

    @Test
    fun savedShape_afterAPick_isTextNameAndBothCoordinates() {
        val vm = vm(FakeSearch())
        vm.onPlacePicked(alamo)
        vm.submit()
        assertEquals(
            mapOf(
                "location" to "Alamo Drafthouse South Lamar",
                "locationName" to "Alamo Drafthouse South Lamar",
                "locationLatitude" to 30.2567,
                "locationLongitude" to -97.7633,
            ),
            placeFields(proposed.single()),
        )
    }

    @Test
    fun savedShape_freeTextAfterAPick_isTheTrimmedTextOnly() {
        val vm = vm(FakeSearch())
        vm.onPlacePicked(alamo)
        vm.onWhereChange("  my backyard ")
        vm.submit()
        assertEquals(mapOf("location" to "my backyard"), placeFields(proposed.single()))
    }

    @Test
    fun closingTheComposer_endsTheSearchSession() {
        val search = FakeSearch()
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { vm(search) } })[PlanComposerViewModel::class]
        store.clear()
        assertEquals(1, search.sessionsEnded)
    }
}
