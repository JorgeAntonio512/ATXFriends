//
//  LocationPermissionManager.swift
//  Avenue3
//

import Combine
import CoreLocation

extension CLLocationCoordinate2D {
    /// Canonical Austin, TX coordinate used as the city-center reference point.
    static let austin = CLLocationCoordinate2D(latitude: 30.2672, longitude: -97.7431)
}

extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

extension CLAuthorizationStatus {
    /// Human-readable label for logging — the raw NSObject description of this imported
    /// ObjC enum is not reliably readable.
    var debugLabel: String {
        switch self {
        case .notDetermined: return "notDetermined"
        case .restricted: return "restricted"
        case .denied: return "denied"
        case .authorizedAlways: return "authorizedAlways"
        case .authorizedWhenInUse: return "authorizedWhenInUse"
        @unknown default: return "unknown(\(rawValue))"
        }
    }
}

@MainActor
class LocationPermissionManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var authorizationStatus: CLAuthorizationStatus
    @Published var currentLocation: CLLocationCoordinate2D?
    /// Non-nil when a location fix attempt has definitively failed — either a terminal
    /// CLError (e.g. system Location Services off) or our own fetch timeout below.
    /// Reset to nil at the start of every new attempt so a repeat failure still produces
    /// a nil -> message transition that `.onChange` observers can react to.
    @Published var lastErrorMessage: String?

    private let locationManager = CLLocationManager()
    private var fetchTimeoutTask: Task<Void, Never>?
    /// Fix acquisition with reduced accuracy is normally near-instant. This is a backstop
    /// for a fetch that never resolves (bad signal, Simulator with location set to "None",
    /// system Location Services disabled) — not a tuning knob for expected GPS latency.
    private let fetchTimeoutSeconds: UInt64 = 12

    override init() {
        self.authorizationStatus = locationManager.authorizationStatus
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyReduced
        locationManager.allowsBackgroundLocationUpdates = false
    }

    /// Requests permission if not yet determined, or starts location updates if already authorized.
    func requestOrStart() async {
        lastErrorMessage = nil
        let status = locationManager.authorizationStatus
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            beginFetch()
        } else {
            locationManager.requestWhenInUseAuthorization()
        }
    }

    /// Re-reads the authorization status directly from CLLocationManager (the instance
    /// property, not the deprecated `CLLocationManager.authorizationStatus()` type method).
    /// Used when the app returns to the foreground so a change made in Settings is picked
    /// up even if the `locationManagerDidChangeAuthorization` delegate callback is slow to fire.
    func refreshAuthorizationStatus() {
        let newStatus = locationManager.authorizationStatus
        print("📍 [LocationPermissionManager] refreshAuthorizationStatus() read instance property: \(newStatus.debugLabel) (previously published: \(authorizationStatus.debugLabel))")
        authorizationStatus = newStatus
    }

    /// Starts the actual GPS fix wait (as opposed to the OS permission-prompt wait, which has
    /// no timeout of its own — the prompt is user-paced, not hardware-paced). Scheduling the
    /// timeout here, rather than whenever a caller taps "Continue", is what keeps a slow human
    /// response to the system dialog from ever being mistaken for a stuck fetch.
    private func beginFetch() {
        locationManager.startUpdatingLocation()
        fetchTimeoutTask?.cancel()
        fetchTimeoutTask = Task { [weak self, fetchTimeoutSeconds] in
            try? await Task.sleep(for: .seconds(fetchTimeoutSeconds))
            guard !Task.isCancelled else { return }
            guard let self, self.currentLocation == nil else { return }
            print("📍 [LocationPermissionManager] fetch timed out after \(fetchTimeoutSeconds)s with no fix")
            self.locationManager.stopUpdatingLocation()
            self.lastErrorMessage = "Couldn't get your location in time."
        }
    }

    private func clearFetchTimeout() {
        fetchTimeoutTask?.cancel()
        fetchTimeoutTask = nil
    }

    // MARK: - CLLocationManagerDelegate

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let newStatus = manager.authorizationStatus
        print("📍 [LocationPermissionManager] locationManagerDidChangeAuthorization delegate fired: \(newStatus.debugLabel)")
        Task { @MainActor in
            authorizationStatus = newStatus
            if newStatus == .authorizedWhenInUse || newStatus == .authorizedAlways {
                beginFetch()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            if let location = locations.last {
                print("📍 [LocationPermissionManager] didUpdateLocations fired: \(location.coordinate)")
                clearFetchTimeout()
                currentLocation = location.coordinate
                manager.stopUpdatingLocation()
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("📍 [LocationPermissionManager] didFailWithError: \(error.localizedDescription)")
        if let clError = error as? CLError, clError.code == .locationUnknown {
            // Transient — CoreLocation keeps retrying internally and may still deliver a fix
            // or a different, terminal error shortly. The fetch timeout above is the backstop
            // if it never resolves; treating this specific code as terminal would turn normal
            // "still acquiring a fix" hiccups into false failures.
            return
        }
        Task { @MainActor in
            clearFetchTimeout()
            lastErrorMessage = error.localizedDescription
        }
    }
}
