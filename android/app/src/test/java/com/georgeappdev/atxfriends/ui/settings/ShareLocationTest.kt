package com.georgeappdev.atxfriends.ui.settings

import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.location.LocationSharing
import com.georgeappdev.atxfriends.location.LocationSource
import com.georgeappdev.atxfriends.location.withLocationSharing
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareLocationTest {

    private class FakeLocation(var granted: Boolean = true, var fix: Coordinate? = Coordinate(30.30123, -97.70456)) : LocationSource {
        var fetches = 0
        override fun hasPermission() = granted
        override suspend fun fetchOnce(): Coordinate? {
            fetches++
            return fix
        }
    }

    private val session = TestSession()
    private val writer = FakeProfileWriter()
    private val location = FakeLocation()
    private var now = FIXED_NOW
    private var signedInUid: String? = session.profile.id

    private val sharing = LocationSharing(
        currentUid = { signedInUid },
        fetchUser = { UserDoc.Found(session.profile) },
        writer = writer,
        location = location,
        onWritten = { _, mode, fix, at -> session.manager.profileChanged(session.profile.withLocationSharing(mode, fix, at)) },
        clock = { now },
    )

    private fun vm() = SettingsViewModel(session.manager, { UserDoc.Found(session.profile) }, sharing, location)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    // What each mode writes.

    @Test
    fun off_writesOnlyTheMode_andKeepsTheStoredCoordinate() = runTest {
        sharing.setMode("uid-sam", LocationSharingMode.OFF, withFix = false)
        assertEquals(mapOf("locationSharingMode" to "off"), writer.lastFields)
        assertEquals(0, location.fetches)
        assertEquals(30.27, session.profile.latitude, 0.0)
    }

    @Test
    fun once_writesModeAndSnappedFix() = runTest {
        sharing.setMode("uid-sam", LocationSharingMode.ONCE, withFix = true)
        val f = writer.lastFields
        assertEquals("once", f["locationSharingMode"])
        assertEquals(30.3, f["latitude"])
        assertEquals(-97.7, f["longitude"])
        assertEquals(GeoPoint(30.3, -97.7), f["location"])
        assertTrue("locationUpdatedAt" in f)
        assertFalse("updatedAt" in f)
    }

    @Test
    fun whenFetchFails_theModeIsStillSaved_withoutACoordinate() = runTest {
        location.fix = null
        sharing.setMode("uid-sam", LocationSharingMode.ON_OPEN, withFix = true)
        assertEquals(mapOf("locationSharingMode" to "onOpen"), writer.lastFields)
    }

    @Test
    fun updateNow_writesOnlyWithAFix() = runTest {
        location.fix = null
        assertNull(sharing.updateNow("uid-sam"))
        assertTrue(writer.writes.isEmpty())
        location.fix = Coordinate(30.3, -97.7)
        sharing.updateNow("uid-sam")
        assertEquals("once", writer.lastFields["locationSharingMode"])
    }

    // "When I open the app".

    private fun storedMode(mode: LocationSharingMode) =
        session.manager.profileChanged(session.profile.copy(locationSharingMode = mode, locationUpdatedAt = FIXED_NOW.minusSeconds(3600)))

    @Test
    fun onOpen_writesWhenModeIsOnOpen_andPastTheThrottle() = runTest {
        storedMode(LocationSharingMode.ON_OPEN)
        sharing.onAppForeground()
        assertEquals("onOpen", writer.lastFields["locationSharingMode"])
        assertEquals(30.3, writer.lastFields["latitude"])
    }

    @Test
    fun onOpen_doesNothingForOtherModes_withoutPermission_orWhenSignedOut() = runTest {
        storedMode(LocationSharingMode.ONCE)
        sharing.onAppForeground()
        storedMode(LocationSharingMode.ON_OPEN)
        location.granted = false
        sharing.onAppForeground()
        location.granted = true
        signedInUid = null
        sharing.onAppForeground()
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun onOpen_throttled_withinFifteenMinutes_orUnderHalfAMile() = runTest {
        session.manager.profileChanged(session.profile.copy(locationSharingMode = LocationSharingMode.ON_OPEN, locationUpdatedAt = FIXED_NOW.minusSeconds(5 * 60)))
        sharing.onAppForeground()
        session.manager.profileChanged(session.profile.copy(locationUpdatedAt = FIXED_NOW.minusSeconds(3600)))
        location.fix = Coordinate(30.2705, -97.7402) // a few hundred feet from the stored 30.27, -97.74
        sharing.onAppForeground()
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun onOpen_neverUndoesAnOffPickedWhileTheFixWasLoading() = runTest {
        storedMode(LocationSharingMode.ON_OPEN)
        val slow = object : LocationSource {
            override fun hasPermission() = true
            override suspend fun fetchOnce(): Coordinate? {
                // The user switches to Off in Settings while this fix is being fetched.
                session.manager.profileChanged(session.profile.copy(locationSharingMode = LocationSharingMode.OFF))
                return Coordinate(30.4, -97.7)
            }
        }
        val racing = LocationSharing({ "uid-sam" }, { UserDoc.Found(session.profile) }, writer, slow, { _, _, _, _ -> }, { now })
        racing.onAppForeground()
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun onOpen_writeFailure_isSwallowed() = runTest {
        storedMode(LocationSharingMode.ON_OPEN)
        writer.failAll = true
        sharing.onAppForeground() // must not throw
    }

    // The Settings card.

    @Test
    fun picker_showsTheSavedMode() {
        assertEquals(LocationSharingMode.ON_OPEN, vm().state.value.share.selected) // sample doc is onOpen
    }

    @Test
    fun switchingToOff_writesImmediately() {
        val vm = vm()
        vm.selectShareMode(LocationSharingMode.OFF)
        assertEquals(mapOf("locationSharingMode" to "off"), writer.lastFields)
        assertEquals(LocationSharingMode.OFF, vm.state.value.share.selected)
        assertEquals(LocationSharingMode.OFF, session.profile.locationSharingMode)
    }

    @Test
    fun switchingOn_withPermission_doesNotAsk_andWritesAFix() {
        val vm = vm()
        vm.selectShareMode(LocationSharingMode.ONCE)
        assertNull(vm.state.value.share.permissionRequestFor)
        assertEquals("once", writer.lastFields["locationSharingMode"])
        assertEquals(30.3, writer.lastFields["latitude"])
    }

    @Test
    fun switchingOn_withoutPermission_asksFirst_thenWritesWhenGranted() {
        location.granted = false
        val vm = vm()
        vm.selectShareMode(LocationSharingMode.OFF)
        writer.writes.clear()
        vm.selectShareMode(LocationSharingMode.ONCE)
        assertEquals(LocationSharingMode.ONCE, vm.state.value.share.permissionRequestFor)
        assertTrue("nothing written while asking", writer.writes.isEmpty())
        location.granted = true
        vm.onPermissionResult(true)
        assertNull(vm.state.value.share.permissionRequestFor)
        assertEquals("once", writer.lastFields["locationSharingMode"])
    }

    @Test
    fun deniedPermission_revertsToOff_withTheIosMessage() {
        location.granted = false
        val vm = vm()
        vm.selectShareMode(LocationSharingMode.OFF)
        vm.selectShareMode(LocationSharingMode.ON_OPEN)
        vm.onPermissionResult(false)
        val s = vm.state.value.share
        assertTrue(s.showPermissionDenied)
        assertEquals(LocationSharingMode.OFF, s.selected)
        assertEquals(mapOf("locationSharingMode" to "off"), writer.lastFields)
    }

    @Test
    fun failedWrite_isVisible_andThePickerFallsBackToTheSavedMode() {
        writer.failAll = true
        val vm = vm()
        vm.selectShareMode(LocationSharingMode.OFF)
        val s = vm.state.value.share
        assertTrue(s.saveFailed)
        assertFalse(s.isUpdating)
        assertEquals(LocationSharingMode.ON_OPEN, s.selected)
    }

    @Test
    fun updateNow_withoutPermission_revertsToOff() {
        location.granted = false
        val vm = vm()
        vm.updateNow()
        assertTrue(vm.state.value.share.showPermissionDenied)
        assertEquals(mapOf("locationSharingMode" to "off"), writer.lastFields)
    }
}
