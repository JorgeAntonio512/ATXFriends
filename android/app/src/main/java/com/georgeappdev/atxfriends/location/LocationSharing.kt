package com.georgeappdev.atxfriends.location

import android.util.Log
import com.georgeappdev.atxfriends.data.model.LocationSharingMode
import com.georgeappdev.atxfriends.data.model.UserProfile
import com.georgeappdev.atxfriends.data.repository.ProfileWriter
import com.georgeappdev.atxfriends.data.repository.UserDoc
import com.georgeappdev.atxfriends.data.repository.UserWrites
import com.georgeappdev.atxfriends.domain.location.CoarseLocation
import com.georgeappdev.atxfriends.domain.location.Coordinate
import com.georgeappdev.atxfriends.domain.location.OnOpenThrottle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * "Share My Location" writes (iOS LocationSharingManager + ProfileViewModel.updateLocationSharing).
 * Permission prompts belong to the screen; this class only reads whether access is granted.
 */
class LocationSharing(
    private val currentUid: () -> String?,
    private val fetchUser: suspend (String) -> UserDoc,
    private val writer: ProfileWriter,
    private val location: LocationSource,
    /** Called after every successful write, so the in-memory profile can follow. */
    private val onWritten: (uid: String, mode: LocationSharingMode, fix: Coordinate?, at: Instant) -> Unit,
    private val clock: () -> Instant = Instant::now,
) {
    /** One location-sharing write at a time, so a mode picked in Settings is never undone. */
    private val writeLock = Mutex()

    /**
     * Saves [mode]. With [withFix], fetches a location first and writes it too when the fetch
     * works; a failed or timed-out fetch still saves the mode, keeping the old stored location
     * (as iOS does — "no scary error"). Returns the fix written, if any. Throws if the write fails.
     */
    suspend fun setMode(uid: String, mode: LocationSharingMode, withFix: Boolean): Coordinate? {
        val fix = if (withFix && location.hasPermission()) location.fetchOnce()?.let(CoarseLocation::snap) else null
        return writeLock.withLock {
            val now = clock()
            writer.update(uid, UserWrites.locationSharing(mode, fix, now))
            onWritten(uid, mode, fix, now)
            fix
        }
    }

    /**
     * The "Update now" button (mode stays "Update once"). Unlike [setMode], nothing is written
     * when the fetch fails — iOS just returns. Returns the fix written, or null.
     */
    suspend fun updateNow(uid: String): Coordinate? {
        val fix = location.fetchOnce()?.let(CoarseLocation::snap) ?: return null
        return writeLock.withLock {
            val now = clock()
            writer.update(uid, UserWrites.locationSharing(LocationSharingMode.ONCE, fix, now))
            onWritten(uid, LocationSharingMode.ONCE, fix, now)
            fix
        }
    }

    /**
     * iOS `handleAppForeground`, run each time the app comes to the foreground: only for
     * "When I open the app", only if permission is already granted (never prompts), and only
     * past the 15-minute / 0.5-mile throttle. Failures are logged, never shown.
     */
    suspend fun onAppForeground() {
        val uid = currentUid() ?: return
        try {
            val user = (fetchUser(uid) as? UserDoc.Found)?.profile ?: return
            if (user.locationSharingMode != LocationSharingMode.ON_OPEN) return
            if (!location.hasPermission()) return
            val raw = location.fetchOnce() ?: return
            val decision = OnOpenThrottle.decide(
                now = clock(),
                fix = raw,
                stored = OnOpenThrottle.storedCoordinate(user.latitude, user.longitude),
                lastUpdatedAt = user.locationUpdatedAt,
            )
            if (decision != OnOpenThrottle.Decision.WRITE) {
                Log.d(TAG, "onOpen write skipped: $decision")
                return
            }
            val fix = CoarseLocation.snap(raw)
            writeLock.withLock {
                // The fetch can take up to 10 s; if the user changed the mode meanwhile, stop.
                val latest = (fetchUser(uid) as? UserDoc.Found)?.profile ?: return
                if (latest.locationSharingMode != LocationSharingMode.ON_OPEN) return
                val now = clock()
                writer.update(uid, UserWrites.locationSharing(LocationSharingMode.ON_OPEN, fix, now))
                onWritten(uid, LocationSharingMode.ON_OPEN, fix, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "onOpen location update failed", e)
        }
    }

    private companion object {
        const val TAG = "LocationSharing"
    }
}

/** The profile after a successful location-sharing write. */
fun UserProfile.withLocationSharing(mode: LocationSharingMode, fix: Coordinate?, at: Instant): UserProfile =
    if (fix == null) copy(locationSharingMode = mode)
    else copy(locationSharingMode = mode, latitude = fix.latitude, longitude = fix.longitude, locationUpdatedAt = at)
