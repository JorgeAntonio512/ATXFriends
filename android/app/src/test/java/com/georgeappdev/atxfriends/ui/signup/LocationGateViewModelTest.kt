package com.georgeappdev.atxfriends.ui.signup

import com.georgeappdev.atxfriends.domain.location.AustinGate
import com.georgeappdev.atxfriends.domain.location.Coordinate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Every outcome of the location gate (LocationGateView / PermissionNeeded / Waitlist). */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val austin = AustinGate.AUSTIN
    private val newYork = Coordinate(40.7128, -74.0060)

    private val permission = MutableStateFlow(false)
    private val locationOn = MutableStateFlow(true)
    private var fix: suspend () -> Coordinate? = { austin }
    private var fetches = 0
    private val waitlisted = mutableListOf<String>()
    private var waitlistError: Exception? = null

    /** Records what a pending (Google) account's gate did. */
    private inner class FakeOutcome : GateOutcome {
        val events = mutableListOf<String>()
        var passError: Exception? = null
        var abandonError: Exception? = null
        override suspend fun passed(coordinate: Coordinate) {
            events += "passed"
            passError?.let { throw it }
        }
        override suspend fun abandoned() {
            events += "abandoned"
            abandonError?.let { throw it }
        }
    }

    private fun gate(outcome: GateOutcome? = null) = LocationGateViewModel(
        path = if (outcome == null) "email" else "google",
        location = { fetches++; fix() },
        permissionGranted = permission,
        locationOn = locationOn,
        joinWaitlist = { email -> waitlistError?.let { throw it }; waitlisted += email },
        outcome = outcome,
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    @Test
    fun emailPath_inRange_passesWithTheCoordinate_andCreatesNothing() = test {
        val vm = gate()
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(austin, vm.state.value.passedAt)
        assertEquals(GateScreen.GATE, vm.state.value.screen)
        assertFalse(vm.state.value.isChecking)
        vm.passConsumed()
        assertNull(vm.state.value.passedAt)
    }

    @Test
    fun pendingAccount_inRange_createsTheDoc() = test {
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(listOf("passed"), outcome.events)
        assertNull(vm.state.value.passedAt)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun outOfRange_showsWaitlist_andCreatesNothing() = test {
        fix = { newYork }
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(GateScreen.WAITLIST, vm.state.value.screen)
        assertTrue(outcome.events.isEmpty())
    }

    @Test
    fun noPermission_asksTheSystem_andDenialShowsPermissionNeeded_notWaitlist() = test {
        val vm = gate()
        var asked = 0
        backgroundScope.launch { vm.requestPermission.collect { asked++ } }
        vm.onContinue(permissionGranted = false)
        runCurrent()
        assertEquals(1, asked)
        assertEquals(0, fetches)
        vm.onPermissionResult(granted = false)
        assertEquals(GateScreen.PERMISSION_NEEDED, vm.state.value.screen)
        assertEquals(0, fetches)
    }

    @Test
    fun grantedInTheDialog_fetchesAndPasses() = test {
        val vm = gate()
        vm.onContinue(permissionGranted = false)
        vm.onPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(austin, vm.state.value.passedAt)
    }

    /** The iOS resume bug: deny → Settings → allow → back must move on with no tap. */
    @Test
    fun allowedInSettingsWhileBlocked_resumesOnItsOwn() = test {
        val vm = gate()
        runCurrent()
        vm.onContinue(permissionGranted = false)
        vm.onPermissionResult(granted = false)
        assertEquals(GateScreen.PERMISSION_NEEDED, vm.state.value.screen)

        permission.value = true // the monitor saw the grant — no user action on our screen
        advanceUntilIdle()
        assertEquals(GateScreen.GATE, vm.state.value.screen)
        assertEquals(1, fetches)
        assertEquals(austin, vm.state.value.passedAt)
    }

    @Test
    fun resumeOnlyHappensOnce_evenIfSignalsRepeat() = test {
        val vm = gate()
        runCurrent()
        vm.onPermissionResult(granted = false)
        permission.value = true
        runCurrent()
        permission.value = false
        permission.value = true
        advanceUntilIdle()
        assertEquals(1, fetches)
    }

    @Test
    fun permissionChangesElsewhere_doNothing() = test {
        val vm = gate()
        runCurrent()
        permission.value = true
        advanceUntilIdle()
        assertEquals(0, fetches)
        assertEquals(GateScreen.GATE, vm.state.value.screen)
    }

    @Test
    fun noFix_stopsSpinning_showsRetry_andContinueTriesAgain() = test {
        fix = { null } // the 12 s timeout / no provider
        val vm = gate()
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.isChecking)
        assertTrue(vm.state.value.locationFailed)

        fix = { austin }
        vm.onContinue(permissionGranted = true)
        assertFalse("retry clears the message", vm.state.value.locationFailed)
        advanceUntilIdle()
        assertEquals(2, fetches)
        assertEquals(austin, vm.state.value.passedAt)
    }

    @Test
    fun whileChecking_continueIsIgnored() = test {
        val pending = CompletableDeferred<Coordinate?>()
        fix = { pending.await() }
        val vm = gate()
        vm.onContinue(permissionGranted = true)
        runCurrent()
        assertTrue(vm.state.value.isChecking)
        vm.onContinue(permissionGranted = true)
        vm.onContinue(permissionGranted = true)
        runCurrent()
        assertEquals(1, fetches)
        pending.complete(austin)
        advanceUntilIdle()
        assertEquals(austin, vm.state.value.passedAt)
    }

    @Test
    fun emailPath_back_justLeaves() = test {
        val vm = gate()
        vm.dismissAll()
        assertTrue(vm.state.value.dismissed)
    }

    @Test
    fun pendingAccount_back_deletesTheAccount() = test {
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.dismissAll()
        advanceUntilIdle()
        assertEquals(listOf("abandoned"), outcome.events)
    }

    @Test
    fun pendingAccount_notNowOnPermissionNeeded_deletesTheAccount() = test {
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onPermissionResult(granted = false)
        vm.dismissAll()
        advanceUntilIdle()
        assertEquals(listOf("abandoned"), outcome.events)
    }

    @Test
    fun waitlist_writesTheEmail_thenAutoDismissesAfterTwoSeconds() = test {
        fix = { newYork }
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()

        vm.onWaitlistEmailChange("not an email")
        vm.submitWaitlist()
        runCurrent()
        assertTrue("invalid email isn't submitted", waitlisted.isEmpty())

        vm.onWaitlistEmailChange("Sam@Example.com")
        vm.submitWaitlist()
        vm.submitWaitlist() // double tap
        runCurrent()
        assertEquals(listOf("Sam@Example.com"), waitlisted)
        assertTrue(vm.state.value.waitlistSubmitted)
        assertTrue(outcome.events.isEmpty())

        advanceTimeBy(2_001)
        assertEquals("the pending account is deleted even without a tap", listOf("abandoned"), outcome.events)
    }

    @Test
    fun waitlist_doneBeforeTheTimer_deletesOnlyOnce() = test {
        fix = { newYork }
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        vm.onWaitlistEmailChange("sam@example.com")
        vm.submitWaitlist()
        runCurrent()
        vm.dismissAll()
        advanceUntilIdle()
        assertEquals(listOf("abandoned"), outcome.events)
    }

    @Test
    fun waitlist_failure_isVisible_keepsTheEmail_andCanRetry() = test {
        fix = { newYork }
        val vm = gate()
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        waitlistError = IOException("offline")
        vm.onWaitlistEmailChange("sam@example.com")
        vm.submitWaitlist()
        advanceUntilIdle()
        assertTrue(vm.state.value.waitlistFailed)
        assertFalse(vm.state.value.waitlistSubmitted)
        assertEquals("sam@example.com", vm.state.value.waitlistEmail)
        waitlistError = null
        vm.submitWaitlist()
        advanceUntilIdle()
        assertTrue(vm.state.value.waitlistSubmitted)
    }

    @Test
    fun createFailure_showsAccountError_tryAgainRetriesTheSameCoordinate() = test {
        val outcome = FakeOutcome().apply { passError = IOException("offline") }
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(GateFailure.CREATE_FAILED, vm.state.value.failure)
        outcome.passError = null
        vm.retryFailure()
        advanceUntilIdle()
        assertEquals(listOf("passed", "passed"), outcome.events)
        assertEquals(1, fetches)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun createFailure_cancel_deletesTheAccount() = test {
        val outcome = FakeOutcome().apply { passError = IOException("offline") }
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        vm.cancelAfterFailure()
        advanceUntilIdle()
        assertEquals(listOf("passed", "abandoned"), outcome.events)
    }

    @Test
    fun cancelFailure_isVisible_andRetryable() = test {
        val outcome = FakeOutcome().apply { abandonError = IOException("offline") }
        val vm = gate(outcome)
        vm.dismissAll()
        advanceUntilIdle()
        assertEquals(GateFailure.CANCEL_FAILED, vm.state.value.failure)
        outcome.abandonError = null
        vm.retryFailure()
        advanceUntilIdle()
        assertEquals(listOf("abandoned", "abandoned"), outcome.events)
        assertNull(vm.state.value.failure)
    }

    @Test
    fun doubleTapWhileTheDialogIsOpen_asksOnce() = test {
        val vm = gate()
        var asked = 0
        backgroundScope.launch { vm.requestPermission.collect { asked++ } }
        vm.onContinue(permissionGranted = false)
        runCurrent()
        vm.onContinue(permissionGranted = false)
        runCurrent()
        assertEquals(1, asked)
        vm.onPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(austin, vm.state.value.passedAt)
    }

    @Test
    fun leavingStopsTheSpinner_andALeftGateNeverResumes() = test {
        val pending = CompletableDeferred<Coordinate?>()
        fix = { pending.await() }
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        runCurrent()
        vm.dismissAll()
        advanceUntilIdle()
        assertFalse(vm.state.value.isChecking)

        val blocked = gate(FakeOutcome().also { })
        runCurrent()
        blocked.onPermissionResult(granted = false)
        blocked.dismissAll()
        permission.value = true
        advanceUntilIdle()
        assertEquals("no fetch after leaving", 1, fetches)
    }

    /** The Pixel bug: permission granted but the phone's Location switch off → not "in time". */
    @Test
    fun locationSwitchOff_showsTurnOnLocation_withoutFetching_andResumesWhenTurnedOn() = test {
        locationOn.value = false
        val vm = gate()
        runCurrent()
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(GateScreen.LOCATION_OFF, vm.state.value.screen)
        assertFalse(vm.state.value.locationFailed)
        assertEquals(0, fetches)

        locationOn.value = true // turned on in Settings; no tap in the app
        advanceUntilIdle()
        assertEquals(1, fetches)
        assertEquals(austin, vm.state.value.passedAt)
    }

    @Test
    fun locationSwitchTurnedOffDuringTheFetch_showsTurnOnLocation() = test {
        fix = { locationOn.value = false; null }
        val vm = gate()
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        assertEquals(GateScreen.LOCATION_OFF, vm.state.value.screen)
    }

    @Test
    fun grantedInTheDialogWithLocationOff_showsTurnOnLocation() = test {
        locationOn.value = false
        val vm = gate()
        vm.onContinue(permissionGranted = false)
        vm.onPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(GateScreen.LOCATION_OFF, vm.state.value.screen)
    }

    @Test
    fun leavingTheLocationOffScreen_neverResumes() = test {
        locationOn.value = false
        val outcome = FakeOutcome()
        val vm = gate(outcome)
        vm.onContinue(permissionGranted = true)
        advanceUntilIdle()
        vm.dismissAll()
        locationOn.value = true
        advanceUntilIdle()
        assertEquals(0, fetches)
        assertEquals(listOf("abandoned"), outcome.events)
    }
}
