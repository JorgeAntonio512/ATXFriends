package com.georgeappdev.atxfriends.domain.distance

import com.georgeappdev.atxfriends.data.model.UserProfile
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Port of iOS DistanceDisplay.swift: distances are always bucketed, never exact, and hidden
 * entirely when the other person isn't sharing their location.
 */
object DistanceDisplay {

    /** Null when the line shouldn't be shown (not sharing, or no distance). */
    fun label(miles: Double?, isSharing: Boolean): String? {
        if (!isSharing || miles == null) return null
        return bucketLabel(miles)
    }

    fun bucketLabel(miles: Double): String = when {
        miles < 1 -> "Under 1 mi away"
        miles < 3 -> "~2 mi away"
        miles < 7 -> "~5 mi away"
        miles < 12 -> "~10 mi away"
        miles < 20 -> "~15 mi away"
        miles < 35 -> "~25 mi away"
        miles < 60 -> "~50 mi away"
        else -> "50+ mi away"
    }

    /**
     * iOS `MatchWithUser.distanceText`: null if either person has no location on file
     * (both coordinates exactly 0) or the other person's Share My Location is off.
     */
    fun between(me: UserProfile, other: UserProfile): String? {
        if (me.latitude == 0.0 && me.longitude == 0.0) return null
        if (other.latitude == 0.0 && other.longitude == 0.0) return null
        val miles = GeoDistance.meters(me.latitude, me.longitude, other.latitude, other.longitude) / METERS_PER_MILE
        return label(miles, other.locationSharingMode.isSharing)
    }

    /** The conversion iOS uses everywhere. */
    const val METERS_PER_MILE = 1609.34
}

/**
 * Ellipsoidal (WGS-84) distance via Vincenty's inverse formula. iOS uses
 * `CLLocation.distance(from:)`, which is also ellipsoidal but whose exact algorithm Apple
 * doesn't document; results agree to well under 1% at these ranges.
 */
object GeoDistance {
    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val B = (1 - F) * A

    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val l = Math.toRadians(lon2 - lon1)
        val u1 = atan((1 - F) * tan(Math.toRadians(lat1)))
        val u2 = atan((1 - F) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(u1); val cosU1 = cos(u1)
        val sinU2 = sin(u2); val cosU2 = cos(u2)

        var lambda = l
        var sinSigma: Double; var cosSigma: Double; var sigma: Double
        var cos2Alpha: Double; var cos2SigmaM: Double
        var iterations = 0
        while (true) {
            val sinLambda = sin(lambda); val cosLambda = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
            )
            if (sinSigma == 0.0) return 0.0 // same point
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cos2Alpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cos2Alpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cos2Alpha else 0.0
            val c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha))
            val previous = lambda
            lambda = l + (1 - c) * F * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (kotlin.math.abs(lambda - previous) < 1e-12 || ++iterations >= 200) break
        }
        val uSq = cos2Alpha * (A * A - B * B) / (B * B)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        return B * bigA * (sigma - deltaSigma)
    }
}
