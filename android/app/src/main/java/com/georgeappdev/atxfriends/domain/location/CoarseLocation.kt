package com.georgeappdev.atxfriends.domain.location

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val latitude: Double, val longitude: Double)

/**
 * iOS `CoarseLocation.snap`: rounds to 2 decimal places (~0.7 mi fixed grid) before any
 * coordinate is written where other users can read it. Swift's `.rounded()` rounds halves
 * away from zero, which `Math.round` doesn't do for negatives (Austin's longitude), so this
 * rounds the Swift way.
 */
object CoarseLocation {
    fun snap(c: Coordinate) = Coordinate(round2(c.latitude), round2(c.longitude))

    private fun round2(value: Double): Double {
        val scaled = value * 100
        return sign(scaled) * floor(abs(scaled) + 0.5) / 100
    }
}

/**
 * The "When I open the app" rule from LocationSharingManager.shouldWriteOnOpen: skip if the
 * last write was under 15 minutes ago, or the new fix is under 0.5 mi from the stored one.
 */
object OnOpenThrottle {
    val MIN_INTERVAL: Duration = Duration.ofMinutes(15)
    const val MIN_MOVEMENT_MILES = 0.5

    enum class Decision { WRITE, THROTTLED, NOT_MOVED }

    fun decide(now: Instant, fix: Coordinate, stored: Coordinate?, lastUpdatedAt: Instant?): Decision {
        if (lastUpdatedAt != null && Duration.between(lastUpdatedAt, now) < MIN_INTERVAL) return Decision.THROTTLED
        if (stored != null && milesBetween(fix, stored) < MIN_MOVEMENT_MILES) return Decision.NOT_MOVED
        return Decision.WRITE
    }

    /** iOS treats a stored (0, 0) as "no location yet". */
    fun storedCoordinate(latitude: Double, longitude: Double): Coordinate? =
        if (latitude != 0.0 || longitude != 0.0) Coordinate(latitude, longitude) else null

    /** Great-circle distance; iOS divides CLLocation meters by 1609.34. */
    fun milesBetween(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        val meters = 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
        return meters / 1609.34
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}

/** ShareMyLocationContent.lastUpdatedLabel. */
enum class LastUpdated { TODAY, THIS_WEEK, OVER_A_WEEK_AGO;

    companion object {
        /** "today" by calendar day; otherwise whole 24-hour days elapsed, as Swift's `.day` component. */
        fun of(updatedAt: Instant, now: Instant, zone: ZoneId): LastUpdated {
            if (updatedAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) return TODAY
            return if (Duration.between(updatedAt, now).toDays() < 7) THIS_WEEK else OVER_A_WEEK_AGO
        }
    }
}
