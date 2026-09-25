package com.georgeappdev.atxfriends.domain.location

/**
 * The sign-up geofence from LocationGateView.swift: a new account may only be created within
 * 50 miles (straight line) of Austin's city center. Checked once, at sign-up; existing users
 * never see it.
 */
object AustinGate {
    /** `CLLocationCoordinate2D.austin`. */
    val AUSTIN = Coordinate(30.2672, -97.7431)

    /** `austinGateMaxMiles`. */
    const val MAX_MILES = 50.0

    enum class Outcome { PASS, WAITLIST }

    /** iOS: `CLLocation.distance(from:) / 1609.34`. */
    fun milesFromAustin(c: Coordinate): Double = OnOpenThrottle.milesBetween(c, AUSTIN)

    /** Exactly 50.0 miles still passes (`distanceMiles <= austinGateMaxMiles`). */
    fun evaluate(c: Coordinate): Outcome = if (milesFromAustin(c) <= MAX_MILES) Outcome.PASS else Outcome.WAITLIST
}
