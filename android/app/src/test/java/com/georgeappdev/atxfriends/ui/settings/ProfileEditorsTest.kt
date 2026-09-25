package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.model.DayOfWeek
import com.georgeappdev.atxfriends.data.model.DaySlotCombo
import com.georgeappdev.atxfriends.data.model.ProfileActivity
import com.georgeappdev.atxfriends.data.model.TimeSlot
import com.georgeappdev.atxfriends.data.repository.ActivityCatalog
import com.georgeappdev.atxfriends.data.repository.CatalogActivity
import com.georgeappdev.atxfriends.data.repository.PhotoUploader
import com.georgeappdev.atxfriends.domain.matching.ActivityCategory
import com.georgeappdev.atxfriends.R
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorsTest {

    private val session = TestSession()
    private val writer = FakeProfileWriter()
    private val edits = ProfileEdits(session.manager, writer) { FIXED_NOW }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // Session

    @Test
    fun session_profileChanged_updatesTheReadyProfileInPlace_onlyForTheSignedInUser() {
        val renamed = session.profile.copy(displayName = "Samantha")
        session.manager.profileChanged(renamed)
        assertEquals("Samantha", session.profile.displayName)
        session.manager.profileChanged(renamed.copy(id = "someone-else", displayName = "Nope"))
        assertEquals("Samantha", session.profile.displayName)
    }

    // Display name

    @Test
    fun name_savesTrimmed_andUpdatesTheSession() {
        val vm = EditNameViewModel(edits)
        vm.onNameChange("  Samantha  ")
        vm.save()
        assertEquals(mapOf("displayName" to "Samantha"), writer.lastFields - "updatedAt")
        assertEquals("Samantha", session.profile.displayName)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun name_invalidOrUnchanged_neverWrites() {
        val vm = EditNameViewModel(edits)
        vm.onNameChange("S")
        assertTrue(vm.state.value.showValidationError)
        vm.save()
        vm.onNameChange(session.profile.displayName)
        vm.save()
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun name_failedSave_showsError_keepsInput_andDoesNotLeave() {
        writer.failAll = true
        val vm = EditNameViewModel(edits)
        vm.onNameChange("Samantha")
        vm.save()
        val s = vm.state.value
        assertTrue(s.saveFailed)
        assertFalse(s.done)
        assertFalse(s.isSaving)
        assertEquals("Samantha", s.name)
        assertEquals("Sam", session.profile.displayName)
    }

    @Test
    fun name_blocksDoubleTaps_whileSaving() {
        val gate = CompletableDeferred<Unit>()
        writer.beforeWrite = { gate.await() }
        val vm = EditNameViewModel(edits)
        vm.onNameChange("Samantha")
        vm.save()
        vm.save()
        assertTrue(vm.state.value.isSaving)
        gate.complete(Unit)
        assertEquals(1, writer.writes.size)
    }

    // Radius

    @Test
    fun radius_savesADouble() {
        val vm = RadiusViewModel(edits)
        assertEquals(10, vm.state.value.miles)
        vm.onChange(17)
        vm.save()
        assertEquals(17.0, writer.lastFields["radiusMiles"])
        assertEquals(17.0, session.profile.radiusMiles, 0.0)
    }

    @Test
    fun radius_failureIsVisible() {
        writer.failAll = true
        val vm = RadiusViewModel(edits)
        vm.onChange(20)
        vm.save()
        assertTrue(vm.state.value.saveFailed)
        assertEquals(20, vm.state.value.miles)
    }

    // Activities

    private class FakeCatalog : ActivityCatalog {
        val items = mutableListOf(
            CatalogActivity("a1", "Hiking"), CatalogActivity("a2", "Board Games"), CatalogActivity("a3", "Tacos"),
            CatalogActivity("a4", "Yoga"), CatalogActivity("c1", "Chess"), CatalogActivity("r1", "Rock Climbing"),
        )
        val created = mutableListOf<Pair<String, ActivityCategory>>()
        var failAdd = false

        override suspend fun fetchAll() = items.sortedBy { it.name }
        override suspend fun addCustom(name: String, category: ActivityCategory): CatalogActivity {
            if (failAdd) throw IOException("offline")
            created += name to category
            return CatalogActivity("new-${created.size}", name)
        }
    }

    private val catalog = FakeCatalog()

    @Test
    fun activities_twoMain_isBlocked_andNeverWritten() {
        val vm = ActivitiesViewModel(edits, catalog)
        // Sample profile: Hiking, Board Games, Tacos (Main) + Yoga (Extra).
        vm.remove("a4")
        assertEquals(1, writer.writes.size) // 3 Main, still valid → saved
        vm.remove("a1")
        val s = vm.state.value
        assertEquals(2, s.main.size)
        assertFalse(s.isValid)
        assertFalse("can't leave with 2 Main", s.canLeave)
        assertEquals("the invalid selection is not written", 1, writer.writes.size)
    }

    @Test
    fun activities_validChange_writesAllThreeArrays() {
        val vm = ActivitiesViewModel(edits, catalog)
        vm.pick(CatalogActivity("c1", "Chess"))
        val f = writer.lastFields
        assertEquals(listOf("a1", "a2", "a3", "a4", "c1"), f["activityIDs"])
        assertEquals(listOf(true, true, true, false, false), f["activityIsPrimary"])
        assertEquals(5, session.profile.activities.size)
        assertTrue(vm.state.value.canLeave)
    }

    @Test
    fun activities_starSwapsMain_andSaves() {
        val vm = ActivitiesViewModel(edits, catalog)
        vm.makeMain("a4")
        assertEquals(listOf(true, true, false, true), writer.lastFields["activityIsPrimary"])
    }

    @Test
    fun activities_customName_reusesAnExistingActivity() {
        val vm = ActivitiesViewModel(edits, catalog)
        vm.onSearchChange("  rock   CLIMBING ")
        vm.addCustom(vm.state.value.search, ActivityCategory.SPORTS_AND_FITNESS, fromSheet = false)
        assertTrue(catalog.created.isEmpty())
        assertTrue(vm.state.value.selected.any { it.id == "r1" })
        assertEquals("", vm.state.value.search)
    }

    @Test
    fun activities_customName_createsANewCatalogEntry_andSelectsIt() {
        val vm = ActivitiesViewModel(edits, catalog)
        vm.addCustom("  Pickle   ball ", ActivityCategory.SPORTS_AND_FITNESS, fromSheet = true)
        assertEquals(listOf("Pickle ball" to ActivityCategory.SPORTS_AND_FITNESS), catalog.created)
        assertEquals(ProfileActivity("new-1", "Pickle ball", false), vm.state.value.selected.last())
        assertEquals(1, vm.state.value.customAdded)
    }

    @Test
    fun activities_failedCustomAdd_isVisible_andKeepsTheSearch() {
        catalog.failAdd = true
        val vm = ActivitiesViewModel(edits, catalog)
        vm.onSearchChange("Pickleball")
        vm.addCustom("Pickleball", ActivityCategory.SPORTS_AND_FITNESS, fromSheet = false)
        assertTrue(vm.state.value.addFailed)
        assertEquals("Pickleball", vm.state.value.search)
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun activities_failedSave_isVisible_andRetryWritesTheLatest() {
        writer.failNext = true
        val vm = ActivitiesViewModel(edits, catalog)
        vm.pick(CatalogActivity("c1", "Chess"))
        assertTrue(vm.state.value.saveFailed)
        assertTrue(writer.writes.isEmpty())
        vm.retrySave()
        assertFalse(vm.state.value.saveFailed)
        assertEquals(5, (writer.lastFields["activityIDs"] as List<*>).size)
    }

    @Test
    fun activities_tapsDuringASave_areCoalesced_intoOneLatestWrite() {
        val gate = CompletableDeferred<Unit>()
        var first = true
        writer.beforeWrite = { if (first) { first = false; gate.await() } }
        val vm = ActivitiesViewModel(edits, catalog)
        vm.pick(CatalogActivity("c1", "Chess"))
        vm.remove("a4")
        vm.pick(CatalogActivity("r1", "Rock Climbing"))
        assertTrue(vm.state.value.isSaving)
        assertFalse("can't leave mid-save", vm.state.value.canLeave)
        gate.complete(Unit)
        assertEquals(2, writer.writes.size)
        assertEquals(listOf("a1", "a2", "a3", "c1", "r1"), writer.lastFields["activityIDs"])
    }

    @Test
    fun activities_catalogLoadFailure_isVisible() {
        val failing = object : ActivityCatalog {
            override suspend fun fetchAll(): List<CatalogActivity> = throw IOException("offline")
            override suspend fun addCustom(name: String, category: ActivityCategory) = error("unused")
        }
        assertTrue(ActivitiesViewModel(edits, failing).state.value.catalogFailed)
    }

    // Availability

    @Test
    fun availability_fewerThanThree_isBlocked_andNeverWritten() {
        val vm = AvailabilityViewModel(edits)
        vm.toggle(DayOfWeek.MONDAY, TimeSlot.NIGHT) // removes one of the sample's 3
        assertFalse(vm.state.value.isValid)
        assertFalse(vm.state.value.canLeave)
        assertTrue(writer.writes.isEmpty())
        vm.toggle(DayOfWeek.FRIDAY, TimeSlot.EVENING)
        assertEquals(listOf("Saturday_Wake Up", "Sunday_Owl Hours", "Friday_Evening"), writer.lastFields["daySlotCombos"])
        assertEquals(3, session.profile.daySlotCombos.size)
    }

    @Test
    fun availability_noMaximum() {
        val vm = AvailabilityViewModel(edits)
        DayOfWeek.entries.filter { it != DayOfWeek.UNKNOWN }.forEach { vm.toggle(it, TimeSlot.AFTERNOON) }
        assertEquals(10, (writer.lastFields["daySlotCombos"] as List<*>).size)
    }

    @Test
    fun availability_chipRemove_andFailureVisible() {
        writer.failAll = true
        val vm = AvailabilityViewModel(edits)
        vm.toggle(DayOfWeek.FRIDAY, TimeSlot.EVENING)
        assertTrue(vm.state.value.saveFailed)
        vm.remove(DaySlotCombo("Friday_Evening"))
        assertEquals(3, vm.state.value.count)
    }

    // Photos

    private class FakeUploader : PhotoUploader {
        val uploads = mutableListOf<Pair<Int, Int>>()
        var fail = false
        override suspend fun upload(uid: String, index: Int, jpeg: ByteArray): String {
            if (fail) throw IOException("offline")
            uploads += index to jpeg.size
            return "https://storage/${uid}_photo_$index.jpg?token=new"
        }
    }

    private val uploader = FakeUploader()

    @Test
    fun photos_replaceOneSlot_uploadsThenWritesOnlyThatSlot() {
        val vm = PhotosViewModel(edits, uploader, encoder = null)
        vm.replace(1) { ByteArray(1000) }
        assertEquals(listOf(1 to 1000), uploader.uploads)
        assertEquals(
            listOf("https://a/0.jpg", "https://storage/uid-sam_photo_1.jpg?token=new", "https://a/2.jpg"),
            writer.lastFields["photoURLs"],
        )
        assertEquals(setOf("photoURLs", "updatedAt"), writer.lastFields.keys)
        assertTrue(vm.state.value.uploading.isEmpty())
        assertTrue(1 in vm.state.value.localJpegs)
        assertNull(vm.state.value.error)
    }

    @Test
    fun photos_tenMegabytesOrMore_isRefusedBeforeUpload() {
        val vm = PhotosViewModel(edits, uploader, encoder = null)
        vm.replace(0) { ByteArray(10 * 1024 * 1024) }
        assertTrue(uploader.uploads.isEmpty())
        assertEquals(R.string.photos_too_large, vm.state.value.error)
    }

    @Test
    fun photos_unreadableImage_andUploadFailure_areVisible() {
        val vm = PhotosViewModel(edits, uploader, encoder = null)
        vm.replace(0) { null }
        assertEquals(R.string.photos_process_failed, vm.state.value.error)
        uploader.fail = true
        vm.replace(0) { ByteArray(10) }
        assertEquals(R.string.photos_upload_failed, vm.state.value.error)
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun photos_slotIsLockedWhileUploading() {
        val gate = CompletableDeferred<ByteArray?>()
        val vm = PhotosViewModel(edits, uploader, encoder = null)
        vm.replace(2) { gate.await() }
        vm.replace(2) { ByteArray(5) }
        assertEquals(setOf(2), vm.state.value.uploading)
        gate.complete(ByteArray(7))
        assertEquals(listOf(2 to 7), uploader.uploads)
    }
}
