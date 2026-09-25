package com.georgeappdev.atxfriends.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ATXF"

/** The one location fix the sign-up gate needs (a seam for tests). */
fun interface GateLocationSource {
    /** A coarse fix, or null if none arrived within [GATE_TIMEOUT_MS]. Never throws. */
    suspend fun fetch(): Coordinate?
}

/** LocationPermissionManager.fetchTimeoutSeconds: the backstop for a fix that never comes. */
const val GATE_TIMEOUT_MS = 12_000L

/**
 * iOS LocationPermissionManager's fetch: approximate accuracy (`kCLLocationAccuracyReduced`),
 * foreground only, one fix. Uses FusedLocationProvider; falls back to the platform
 * [LocationSource] if Play services can't serve the request.
 */
class FusedGateLocation(context: Context, private val fallback: LocationSource) : GateLocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission") // The gate only fetches after the permission is granted.
    override suspend fun fetch(): Coordinate? {
        val started = System.currentTimeMillis()
        val fix = withTimeoutOrNull(GATE_TIMEOUT_MS) {
            try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setGranularity(Granularity.GRANULARITY_COARSE)
                    .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
                    .build()
                val cancel = CancellationTokenSource()
                client.getCurrentLocation(request, cancel.token).await(cancel)
                    ?.let { Coordinate(it.latitude, it.longitude) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "gate: fused location failed, trying the platform provider", e)
                fallback.fetchOnce()
            }
        }
        val took = System.currentTimeMillis() - started
        if (fix == null) Log.w(TAG, "gate: no location fix after ${took}ms") else Log.i(TAG, "gate: got a fix in ${took}ms")
        return fix
    }

    private companion object {
        /** A fix up to 5 minutes old is fine for a 50-mile check. */
        const val MAX_FIX_AGE_MS = 5 * 60 * 1000L
    }
}

/**
 * Whether the app may read (approximate) location, kept current from every signal Android
 * offers — not only lifecycle events. iOS got stuck exactly here: deny → Settings → allow →
 * return did nothing until a lifecycle callback that never came. Sources, each logged:
 *  - AppOps' own change callback for the location ops (fires the moment the grant lands),
 *  - [refresh] from the screen: on resume, on return from the Settings screen it opened, and
 *    a light poll while the "we need your location" screen is visible.
 */
class LocationPermissionMonitor(private val context: Context) {
    private val _granted = MutableStateFlow(read())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /**
     * The phone's own Location switch (Settings → Location). With it off no app gets a fix, even
     * with permission — iOS reports that as "denied" and shows the permission screen.
     */
    private val _locationOn = MutableStateFlow(readLocationOn())
    val locationOn: StateFlow<Boolean> = _locationOn.asStateFlow()

    init {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        if (appOps != null) {
            val listener = AppOpsManager.OnOpChangedListener { op, pkg ->
                if (pkg == context.packageName) refresh("appOps($op)")
            }
            runCatching {
                appOps.startWatchingMode(AppOpsManager.OPSTR_COARSE_LOCATION, context.packageName, listener)
                appOps.startWatchingMode(AppOpsManager.OPSTR_FINE_LOCATION, context.packageName, listener)
            }.onFailure { Log.w(TAG, "permission: couldn't watch AppOps", it) }
        }
        // The system announces every flip of the Location switch.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh("locationModeChanged")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply { addAction(LocationManager.PROVIDERS_CHANGED_ACTION) },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "permission: couldn't watch the Location switch", it) }
    }

    /** Re-reads the permission and the Location switch. Logs every change, and every non-poll check. */
    fun refresh(reason: String) {
        val granted = read()
        val on = readLocationOn()
        val changed = granted != _granted.value || on != _locationOn.value
        _granted.value = granted
        _locationOn.value = on
        val summary = "permission ${if (granted) "granted" else "NOT granted"}, Location switch ${if (on) "on" else "OFF"}"
        if (changed) Log.i(TAG, "permission: changed → $summary (via $reason)")
        else if (reason != "poll") Log.d(TAG, "permission: checked via $reason — unchanged ($summary)")
    }

    private fun read(): Boolean =
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) || isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun readLocationOn(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
