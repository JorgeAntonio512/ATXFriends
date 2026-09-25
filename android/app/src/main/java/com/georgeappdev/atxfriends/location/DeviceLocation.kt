package com.georgeappdev.atxfriends.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.georgeappdev.atxfriends.domain.location.Coordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Location access and one-shot fixes (a seam for tests). */
interface LocationSource {
    /** Coarse ("approximate") or fine access is granted. */
    fun hasPermission(): Boolean

    /** One raw fix, or null on failure / after 10 seconds. Never throws. */
    suspend fun fetchOnce(): Coordinate?
}

/**
 * Android's equivalent of LocationSharingManager's fetch: coarse location is enough (the
 * coordinate is snapped to a ~0.7 mi grid before it's written anyway), only while the app is
 * in use, never in the background. Uses the platform LocationManager — no Play Services.
 */
class DeviceLocation(private val context: Context) : LocationSource {

    override fun hasPermission(): Boolean = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Precise" when fine access is granted — only used for the Settings status badge. */
    fun hasPreciseAccess(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun fetchOnce(): Coordinate? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            for (provider in providers(manager)) {
                val location = try {
                    currentLocation(manager, provider)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // e.g. SecurityException: this provider needs fine access on older Android.
                }
                if (location != null) return@withTimeoutOrNull Coordinate(location.latitude, location.longitude)
            }
            null
        }
    }

    private fun providers(manager: LocationManager): List<String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return candidates.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    }

    @SuppressLint("MissingPermission") // Checked in fetchOnce; a revoked permission throws, caught by the caller.
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** LocationSharingManager.fetchTimeoutSeconds. */
        const val TIMEOUT_MS = 10_000L
    }
}
