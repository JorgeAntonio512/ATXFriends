//
//  LocationRepairService.swift
//  Avenue3
//
//  Backfills the real coordinate for accounts whose profile-setup flow overwrote it
//  with the old TimeSlotPickerView stub point (see prelaunch-fix-02). Never prompts —
//  only reads location when permission is already granted, and only once per launch.
//

import Foundation
import CoreLocation

@MainActor
final class LocationRepairService: NSObject, CLLocationManagerDelegate {
    static let shared = LocationRepairService()

    /// The hardcoded point TimeSlotPickerView used to write over every user's real
    /// signup coordinate. Anyone whose stored location still equals this is a candidate
    /// for repair, since it's not a real device fix.
    private static let stubCoordinate = CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
    private static let coordinateTolerance = 0.0001

    private let locationManager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocationCoordinate2D?, Never>?
    private var hasAttemptedThisLaunch = false

    private override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    /// Checks a user's stored coordinate and repairs it in the background if it's the
    /// stub point or unset — but only when location permission is already granted.
    /// Safe to call repeatedly (e.g. on every auth-state recheck); only does real work
    /// once per app launch.
    func repairIfNeeded(for user: FirebaseUser) async {
        guard !hasAttemptedThisLaunch else { return }
        hasAttemptedThisLaunch = true

        let isStub = abs(user.latitude - Self.stubCoordinate.latitude) < Self.coordinateTolerance
            && abs(user.longitude - Self.stubCoordinate.longitude) < Self.coordinateTolerance
        let isUnset = user.latitude == 0 && user.longitude == 0

        guard isStub || isUnset else {
            print("[LocationRepair] uid=\(user.id) action=ok")
            return
        }

        let status = locationManager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else {
            print("[LocationRepair] uid=\(user.id) action=skipped-no-permission")
            return
        }

        guard let coordinate = await fetchLocationOnce() else {
            // Permission was granted but the fetch itself failed (no fix, hardware
            // issue, etc.) — leave the stub in place and let the next launch retry.
            return
        }

        do {
            try await FirestoreService.shared.updateUserLocation(
                userID: user.id,
                latitude: coordinate.latitude,
                longitude: coordinate.longitude
            )
            print("[LocationRepair] uid=\(user.id) action=repaired")
        } catch {
            print("[LocationRepair] uid=\(user.id) failed to save repaired coordinate: \(error)")
        }
    }

    /// Requests a single fix. Never prompts — `requestLocation()` only delivers a fix
    /// when authorization is already granted, which the caller has already verified.
    private func fetchLocationOnce() async -> CLLocationCoordinate2D? {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            locationManager.requestLocation()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            continuation?.resume(returning: locations.last?.coordinate)
            continuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            print("[LocationRepair] location fetch failed: \(error.localizedDescription)")
            continuation?.resume(returning: nil)
            continuation = nil
        }
    }
}
