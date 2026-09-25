package com.georgeappdev.atxfriends.ui.signup

import com.georgeappdev.atxfriends.R
import com.georgeappdev.atxfriends.data.TestDocs
import com.georgeappdev.atxfriends.data.firestore.DocumentUpdate
import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.ActivityCatalog
import com.georgeappdev.atxfriends.data.repository.CatalogActivity
import com.georgeappdev.atxfriends.data.repository.SignupWrites
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.georgeappdev.atxfriends.domain.profile.SetupStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupViewModelTest {

    private val now = Instant.ofEpochSecond(1_770_000_000)

    /** The doc sign-up just created (Google name pre-filled). */
    private val newProfile = UserProfile.fromFirestore("uid-new", SignupWrites.newUser("Sam Rivera", Coordinate(30.3, -97.7), now).fields)!!

    private val catalog = listOf("Board Games", "Coffee", "Hiking", "Tacos", "Yoga").mapIndexed { i, n -> CatalogActivity("c$i", n) }
    private var catalogError: Exception? = null
    private val added = mutableListOf<Pair<String, ActivityCategory>>()

    private val events = mutableListOf<String>()
    private val uploads = mutableListOf<Int>()
    private var uploadError: Exception? = null
    private var uploadGate: CompletableDeferred<Unit>? = null
    private val writes = mutableListOf<DocumentUpdate>()
    private var writeError: Exception? = null
    private var finished = 0

    private fun vm(profile: UserProfile = newProfile) = ProfileSetupViewModel(
        profile = profile,
        catalogSource = object : ActivityCatalog {
            override suspend fun fetchAll(): List<CatalogActivity> {
                catalogError?.let { throw it }
                return catalog
            }
            override suspend fun addCustom(name: String, category: ActivityCategory): CatalogActivity {
                added += name to category
                return CatalogActivity("new-$name", name)
            }
        },
        uploader = { _, index, _ ->
            uploadGate?.await()
            events += "upload$index"
            uploads += index
            uploadError?.let { throw it }
            "https://storage/$index.jpg"
        },
        writer = { _, update ->
            events += "write"
            writeError?.let { throw it }
            writes += update
        },
        onFinished = { events += "finished"; finished++ },
        clock = { now },
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun jpeg(tag: Int) = ByteArray(10) { tag.toByte() }

    /** Fills every step with a valid profile and lands on "You're All Set!". */
    private fun ProfileSetupViewModel.fillEverything() {
        next() // name (pre-filled)
        addPhotos(3) { i -> jpeg(i) }
        next()
        listOf(0, 1, 2, 3).forEach { toggleActivity(catalog[it]) }
        next()
        toggleSlot(DayOfWeek.MONDAY, TimeSlot.NIGHT)
        toggleSlot(DayOfWeek.SATURDAY, TimeSlot.WAKE_UP)
        toggleSlot(DayOfWeek.SUNDAY, TimeSlot.OWL_HOURS)
        next()
        assertEquals(SetupStep.COMPLETE, state.value.step)
    }

    @Test
    fun nameIsPrefilledFromTheDoc_andStepsAreGated() {
        val vm = vm()
        assertEquals("Sam Rivera", vm.state.value.displayName)
        vm.onNameChange("S")
        vm.next()
        assertEquals(SetupStep.NAME, vm.state.value.step)
        assertTrue(vm.state.value.showNameError)
        vm.onNameChange("Sam")
        vm.next()
        assertEquals(SetupStep.PHOTOS, vm.state.value.step)
        vm.next()
        assertEquals("needs exactly 3 photos", SetupStep.PHOTOS, vm.state.value.step)
        vm.back()
        assertEquals(SetupStep.NAME, vm.state.value.step)
        vm.back()
        assertEquals(SetupStep.NAME, vm.state.value.step)
    }

    @Test
    fun photos_keepPickOrder_neverExceedThree_andCanBeRemoved() {
        val vm = vm()
        vm.addPhotos(2) { i -> jpeg(i) }
        assertEquals(1, vm.photosNeeded())
        vm.addPhotos(3) { i -> jpeg(10 + i) }
        assertEquals(listOf(0, 1, 10), vm.state.value.photos.map { it[0].toInt() })
        vm.removePhoto(0)
        assertEquals(listOf(1, 10), vm.state.value.photos.map { it[0].toInt() })
    }

    @Test
    fun unreadablePhoto_isSkipped_withAVisibleMessage() {
        val vm = vm()
        vm.addPhotos(3) { i -> if (i == 1) null else jpeg(i) }
        assertEquals(2, vm.state.value.photos.size)
        assertEquals(R.string.photos_process_failed, vm.state.value.photoError)
    }

    @Test
    fun activities_firstThreeAreMain_cardTapToggles_capIsTen() {
        val vm = vm()
        catalog.take(4).forEach { vm.toggleActivity(it) }
        assertEquals(listOf(true, true, true, false), vm.state.value.activities.map { it.isPrimary })
        vm.toggleActivity(catalog[0]) // deselect a Main → first Extra promoted
        assertEquals(listOf("c1", "c2", "c3"), vm.state.value.mainActivities.map { it.id })

        val many = (0 until 12).map { CatalogActivity("x$it", "X$it") }
        val full = vm()
        many.forEach { full.toggleActivity(it) }
        assertEquals(10, full.state.value.activities.size)
        assertTrue(full.state.value.activityLimitHit)
    }

    @Test
    fun search_showsNothingUntilTyping_thenMatches_andOffersAddOnlyWhenNothingMatches() {
        val vm = vm()
        assertTrue(vm.state.value.searchResults.isEmpty())
        vm.onSearchChange("co")
        assertEquals(listOf("Coffee", "Tacos"), vm.state.value.searchResults.map { it.name })
        assertFalse(vm.state.value.showAddOption)
        vm.onSearchChange("  Pickleball ")
        assertTrue(vm.state.value.showAddOption)
    }

    @Test
    fun customActivity_reusesANearDuplicate_orCreatesAndSelects() {
        val vm = vm()
        vm.onSearchChange("  board   GAMES ")
        vm.addCustomActivity(ActivityCategory.entries.first())
        assertTrue(added.isEmpty())
        assertEquals(listOf("c0"), vm.state.value.activities.map { it.id })
        assertEquals("", vm.state.value.search)

        vm.onSearchChange("Pickle  ball")
        vm.addCustomActivity(ActivityCategory.entries.first())
        assertEquals(listOf("Pickle ball" to ActivityCategory.entries.first()), added)
        assertEquals("new-Pickle ball", vm.state.value.activities.last().id)
    }

    @Test
    fun catalogFailure_isVisible_andRetryable() {
        catalogError = IOException("offline")
        val vm = vm()
        assertTrue(vm.state.value.catalogFailed)
        catalogError = null
        vm.loadCatalog()
        assertFalse(vm.state.value.catalogFailed)
        assertEquals(5, vm.state.value.catalog.size)
    }

    /** Photos first, then one write with isProfileComplete — never "complete" without photos. */
    @Test
    fun enterAtxFriends_uploadsPhotosThenWritesTheProfileOnce_thenRoutes() {
        val vm = vm()
        vm.fillEverything()
        vm.save()
        assertEquals(setOf(0, 1, 2), uploads.toSet())
        assertEquals(listOf("write", "finished"), events.filterNot { it.startsWith("upload") })
        assertTrue(events.indexOf("write") > events.indexOfLast { it.startsWith("upload") })
        val f = writes.single().fields
        assertEquals(true, f["isProfileComplete"])
        assertEquals("Sam Rivera", f["displayName"])
        assertEquals(listOf("https://storage/0.jpg", "https://storage/1.jpg", "https://storage/2.jpg"), f["photoURLs"])
        assertEquals(listOf("c0", "c1", "c2", "c3"), f["activityIDs"])
        assertEquals(listOf(true, true, true, false), f["activityIsPrimary"])
        assertEquals(listOf("Monday_Night", "Saturday_Wake Up", "Sunday_Owl Hours"), f["daySlotCombos"])
        assertFalse(vm.state.value.isSaving)
        assertNull(vm.state.value.saveError)
    }

    @Test
    fun uploadFailure_writesNothing_keepsEverything_andRetryWorks() {
        val vm = vm()
        vm.fillEverything()
        uploadError = IOException("offline")
        vm.save()
        assertTrue(writes.isEmpty())
        assertEquals(0, finished)
        assertEquals(R.string.setup_save_error_photos, vm.state.value.saveError)
        assertEquals(3, vm.state.value.photos.size)
        assertEquals(4, vm.state.value.activities.size)

        vm.dismissSaveError()
        uploadError = null
        vm.save()
        assertEquals(1, writes.size)
        assertEquals(1, finished)
    }

    @Test
    fun writeFailure_isVisible_andNothingRoutes() {
        val vm = vm()
        vm.fillEverything()
        writeError = IOException("denied")
        vm.save()
        assertEquals(R.string.setup_save_error_generic, vm.state.value.saveError)
        assertEquals(0, finished)
        assertEquals(SetupStep.COMPLETE, vm.state.value.step)
    }

    @Test
    fun doubleTapOnEnter_savesOnce() {
        uploadGate = CompletableDeferred()
        val vm = vm()
        vm.fillEverything()
        vm.save()
        vm.save()
        assertTrue(vm.state.value.isSaving)
        uploadGate!!.complete(Unit)
        assertEquals(1, writes.size)
        assertEquals(1, finished)
    }

    @Test
    fun missingLocation_blocksSaving_withTheIosMessage() {
        val noLocation = UserProfile.fromFirestore("uid-x", TestDocs.user() + mapOf("latitude" to 0.0, "isProfileComplete" to false))!!
        val vm = vm(noLocation)
        // Name, activities and times come pre-filled from this doc; only photos are needed.
        vm.next()
        vm.addPhotos(3) { i -> jpeg(i) }
        repeat(3) { vm.next() }
        assertEquals(SetupStep.COMPLETE, vm.state.value.step)
        vm.save()
        assertEquals(R.string.setup_invalid_location, vm.state.value.saveError)
        assertTrue(uploads.isEmpty())
    }
}
