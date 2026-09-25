package com.georgeappdev.atxfriends.ui.signup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.georgeappdev.atxfriends.data.auth.AuthValidation
import com.georgeappdev.atxfriends.domain.location.AustinGate
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.location.GateLocationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which gate screen is showing: iOS's three, plus LOCATION_OFF — permission granted but the
 * phone's Location switch is off (iOS folds that into its permission screen).
 */
enum class GateScreen { GATE, PERMISSION_NEEDED, LOCATION_OFF, WAITLIST }

/** A pending account's create or cancel failed (iOS "Account Error"). */
enum class GateFailure { CREATE_FAILED, CANCEL_FAILED }

data class GateUiState(
    val screen: GateScreen = GateScreen.GATE,
    /** iOS `isCheckingLocation`: permission granted, waiting on a fix. */
    val isChecking: Boolean = false,
    /** iOS `locationErrorMessage` ("Couldn't get your location in time."). */
    val locationFailed: Boolean = false,
    /** Creating the pending account's doc, or deleting it. Blocks every button. */
    val isFinishing: Boolean = false,
    val failure: GateFailure? = null,
    val waitlistEmail: String = "",
    val waitlistSubmitting: Boolean = false,
    val waitlistSubmitted: Boolean = false,
    val waitlistFailed: Boolean = false,
    /** Email path only: the gate passed with this coordinate → show the sign-up form. */
    val passedAt: Coordinate? = null,
    /** Email path only: the person backed out → leave the gate. */
    val dismissed: Boolean = false,
) {
    /** WaitlistView.isValidEmail — same pattern as sign-up. */
    val waitlistEmailValid: Boolean get() = AuthValidation.isValidEmail(waitlistEmail)
    val isBusy: Boolean get() = isChecking || isFinishing
}

/**
 * What happens when the gate passes or the person backs out. The email path has no account yet:
 * passing shows the sign-up form and backing out just leaves. A pending Google account already
 * exists: passing creates its `users` doc, backing out deletes the account (iOS
 * createNewSSOUser / cancelNewSSOSignup). Both throw on failure.
 */
interface GateOutcome {
    suspend fun passed(coordinate: Coordinate)
    suspend fun abandoned()
}

/**
 * Port of iOS LocationGateView + LocationPermissionNeededView + WaitlistView. Deny → Settings →
 * allow → return moves on by itself: the permission state is observed directly (see
 * LocationPermissionMonitor), not inferred from lifecycle events. The fix itself times out
 * after 12 seconds with an inline retry, so the spinner can't hang.
 */
class LocationGateViewModel(
    private val path: String,
    private val location: GateLocationSource,
    permissionGranted: StateFlow<Boolean>,
    /** The phone's Location switch. */
    private val locationOn: StateFlow<Boolean>,
    private val joinWaitlist: suspend (email: String) -> Unit,
    /** Null on the email path. */
    private val outcome: GateOutcome?,
    private val waitlistDismissDelayMs: Long = 2_000,
) : ViewModel() {

    private val _state = MutableStateFlow(GateUiState())
    val state: StateFlow<GateUiState> = _state.asStateFlow()

    private val permissionRequests = Channel<Unit>(Channel.CONFLATED)
    /** Ask the system for approximate location (the UI owns the permission launcher). */
    val requestPermission: Flow<Unit> = permissionRequests.receiveAsFlow()

    private var fetchJob: Job? = null
    private var passedCoordinate: Coordinate? = null
    /** iOS `hasResumed`: the needed-screen resumes once, however many signals arrive. */
    private var resumed = false
    private var dismissStarted = false
    /** The system permission dialog is open; a second tap mustn't open another. */
    private var awaitingPermission = false

    init {
        viewModelScope.launch {
            permissionGranted.collect { granted -> onPermissionChanged(granted) }
        }
        viewModelScope.launch {
            locationOn.collect { on -> onLocationSwitchChanged(on) }
        }
    }

    /** "Continue" (also the retry after a timeout). */
    fun onContinue(permissionGranted: Boolean) {
        val s = _state.value
        if (s.isBusy || s.screen != GateScreen.GATE || awaitingPermission) return
        _state.update { it.copy(locationFailed = false) }
        if (permissionGranted) startFetch("continue")
        else {
            awaitingPermission = true
            permissionRequests.trySend(Unit)
        }
    }

    /** The system permission dialog closed. Denied → the needed screen, never the waitlist. */
    fun onPermissionResult(granted: Boolean) {
        awaitingPermission = false
        if (dismissStarted) return
        log("permission dialog → ${if (granted) "granted" else "denied"}")
        if (granted) startFetch("permissionDialog")
        else _state.update { it.copy(screen = GateScreen.PERMISSION_NEEDED, isChecking = false) }
    }

    private fun onPermissionChanged(granted: Boolean) {
        val s = _state.value
        log("permission state → $granted (screen=${s.screen}, resumed=$resumed)")
        if (!granted || s.screen != GateScreen.PERMISSION_NEEDED || resumed) return
        resumed = true
        log("allowed while blocked — resuming the check on its own")
        _state.update { it.copy(screen = GateScreen.GATE) }
        startFetch("resume")
    }

    private fun onLocationSwitchChanged(on: Boolean) {
        val s = _state.value
        log("Location switch → ${if (on) "on" else "off"} (screen=${s.screen})")
        if (!on || s.screen != GateScreen.LOCATION_OFF || dismissStarted) return
        log("Location turned on while blocked — resuming the check on its own")
        _state.update { it.copy(screen = GateScreen.GATE) }
        startFetch("locationTurnedOn")
    }

    private fun showLocationOff() {
        log("the phone's Location switch is off — asking to turn it on")
        _state.update { it.copy(screen = GateScreen.LOCATION_OFF, isChecking = false, locationFailed = false) }
    }

    private fun startFetch(reason: String) {
        if (fetchJob?.isActive == true) return
        if (!locationOn.value) return showLocationOff()
        log("fetching a fix ($reason)")
        _state.update { it.copy(isChecking = true, locationFailed = false) }
        fetchJob = viewModelScope.launch {
            val fix = try {
                location.fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (fix == null && !locationOn.value) {
                showLocationOff()
            } else if (fix == null) {
                log("no fix — showing retry")
                _state.update { it.copy(isChecking = false, locationFailed = true) }
            } else {
                evaluate(fix)
            }
        }
    }

    private suspend fun evaluate(fix: Coordinate) {
        val miles = AustinGate.milesFromAustin(fix)
        when (AustinGate.evaluate(fix)) {
            AustinGate.Outcome.PASS -> {
                log("[Onboarding] path=$path step=locationGate gate=passed (${"%.1f".format(miles)} mi)")
                passedCoordinate = fix
                if (outcome == null) _state.update { it.copy(isChecking = false, passedAt = fix) }
                else {
                    _state.update { it.copy(isChecking = false) }
                    finish(GateFailure.CREATE_FAILED) { outcome.passed(fix) }
                }
            }
            AustinGate.Outcome.WAITLIST -> {
                log("[Onboarding] path=$path step=waitlist gate=failed (${"%.1f".format(miles)} mi)")
                _state.update { it.copy(isChecking = false, screen = GateScreen.WAITLIST) }
            }
        }
    }

    /** Email path: the sign-up form has been shown for this pass. */
    fun passConsumed() = _state.update { it.copy(passedAt = null) }

    /** "Back" on the gate, "Not now" on the needed screen and waitlist, "Done" on the waitlist. */
    fun dismissAll() {
        if (_state.value.isFinishing) return
        dismissStarted = true
        resumed = true // a gate being left never resumes on its own
        log("[Onboarding] path=$path step=waitlistOrCancel gate=failed")
        fetchJob?.cancel()
        _state.update { it.copy(isChecking = false) }
        if (outcome == null) _state.update { it.copy(dismissed = true) }
        else finish(GateFailure.CANCEL_FAILED) { outcome.abandoned() }
    }

    /** "Try Again" on the Account Error alert: redo whichever step failed. */
    fun retryFailure() {
        when (_state.value.failure) {
            GateFailure.CREATE_FAILED -> {
                val c = passedCoordinate ?: return
                val o = outcome ?: return
                finish(GateFailure.CREATE_FAILED) { o.passed(c) }
            }
            GateFailure.CANCEL_FAILED -> outcome?.let { o -> finish(GateFailure.CANCEL_FAILED) { o.abandoned() } }
            null -> Unit
        }
    }

    /** "Cancel" on the create-failed alert: give up and delete the pending account. */
    fun cancelAfterFailure() {
        _state.update { it.copy(failure = null) }
        dismissAll()
    }

    private fun finish(kind: GateFailure, step: suspend () -> Unit) {
        if (_state.value.isFinishing) return
        _state.update { it.copy(isFinishing = true, failure = null) }
        viewModelScope.launch {
            try {
                step()
                _state.update { it.copy(isFinishing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "gate: ${kind.name} on path=$path", e)
                _state.update { it.copy(isFinishing = false, failure = kind) }
            }
        }
    }

    // Waitlist

    fun onWaitlistEmailChange(value: String) = _state.update { it.copy(waitlistEmail = value, waitlistFailed = false) }

    fun submitWaitlist() {
        val s = _state.value
        if (!s.waitlistEmailValid || s.waitlistSubmitting || s.waitlistSubmitted) return
        _state.update { it.copy(waitlistSubmitting = true, waitlistFailed = false) }
        viewModelScope.launch {
            try {
                joinWaitlist(s.waitlistEmail)
                log("waitlist signup written")
                _state.update { it.copy(waitlistSubmitting = false, waitlistSubmitted = true) }
                // iOS dismisses (deleting any pending account) 2 s later even without a tap.
                delay(waitlistDismissDelayMs)
                if (!dismissStarted) dismissAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "gate: waitlist write failed", e)
                _state.update { it.copy(waitlistSubmitting = false, waitlistFailed = true) }
            }
        }
    }

    private fun log(message: String) = Log.i(TAG, "gate: $message")

    private companion object {
        const val TAG = "ATXF"
    }
}
