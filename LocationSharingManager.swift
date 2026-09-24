//
//  LocationSharingManager.swift
//  Avenue3
//
//  Drives the "Share My Location" setting: permission (When In Use only, never
//  Always/background), one-shot fetches with a timeout, and the throttle rule for
//  the "When I open the app" mode. Coordinates are snapped to a coarse grid before
//  they ever leave this layer — see CoarseLocation.
//

import Foundation
import CoreLocation
import Combine

/// The three "Share My Location" options. Raw values match the Firestore
/// `locationSharingMode` string field.
enum LocationSharingMode: String, CaseIterable {
    case off
    case once
    case onOpen

    var displayName: String {
        switch self {
        case .off: return "Off"
        case .once: return "Update once"
        case .onOpen: return "When I open the app"
        }
    }
}

/// Snaps a coordinate to a fixed ~0.7 mi grid (2 decimal places). A fixed grid,
/// not random jitter — jitter averages out over repeated updates and leaks the
/// true spot, a fixed grid never does. Applied before any coordinate is written
/// anywhere another user's match card can read it.
enum CoarseLocation {
    static func snap(_ coordinate: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(
            latitude: (coordinate.latitude * 100).rounded() / 100,
            longitude: (coordinate.longitude * 100).rounded() / 100
        )
    }
}

@MainActor
final class LocationSharingManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationSharingManager()

    @Published var authorizationStatus: CLAuthorizationStatus

    private let locationManager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocationCoordinate2D?, Never>?
    private var fetchTimeoutTask: Task<Void, Never>?
    private let fetchTimeoutSeconds: UInt64 = 10
    private let throttleInterval: TimeInterval = 15 * 60
    private let minMovementMiles: Double = 0.5

    private override init() {
        self.authorizationStatus = CLLocationManager().authorizationStatus
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyReduced
        // Non-negotiable: When In Use only. Never request Always, never enable
        // background updates.
        locationManager.allowsBackgroundLocationUpdates = false
    }

    // MARK: - Permission

    func refreshAuthorizationStatus() {
        authorizationStatus = locationManager.authorizationStatus
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways
    }

    var isDeniedOrRestricted: Bool {
        authorizationStatus == .denied || authorizationStatus == .restricted
    }

    /// Requests When In Use permission if it hasn't been decided yet, then waits
    /// briefly for the system prompt to resolve so the caller can check the result
    /// immediately after. No-ops if permission is already decided either way.
    func requestPermissionIfNeeded() async {
        refreshAuthorizationStatus()
        #if DEBUG
        print("[LOCSHARE] permission status before request: \(authorizationStatus.debugLabel)")
        #endif
        guard authorizationStatus == .notDetermined else { return }
        locationManager.requestWhenInUseAuthorization()
        for _ in 0..<50 {
            refreshAuthorizationStatus()
            if authorizationStatus != .notDetermined { break }
            try? await Task.sleep(for: .milliseconds(100))
        }
        #if DEBUG
        print("[LOCSHARE] permission status after request: \(authorizationStatus.debugLabel)")
        #endif
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let newStatus = manager.authorizationStatus
        Task { @MainActor in
            print("[LOCSHARE] permission changed: \(newStatus.debugLabel)")
            authorizationStatus = newStatus
        }
    }

    // MARK: - Fetch

    /// Fetches a single fix and snaps it to the coarse grid. Returns nil on
    /// failure or timeout (~10s) — callers should keep the old stored location
    /// and show no scary error; no spinner may hang forever.
    func fetchCoarseLocation() async -> CLLocationCoordinate2D? {
        guard let raw = await fetchRawLocationOnce() else { return nil }
        let snapped = CoarseLocation.snap(raw)
        #if DEBUG
        print("[LOCSHARE] fetch success raw=(\(raw.latitude), \(raw.longitude)) snapped=(\(snapped.latitude), \(snapped.longitude))")
        #endif
        return snapped
    }

    private func fetchRawLocationOnce() async -> CLLocationCoordinate2D? {
        fetchTimeoutTask?.cancel()
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
            locationManager.requestLocation()
            fetchTimeoutTask = Task { [weak self, fetchTimeoutSeconds] in
                try? await Task.sleep(for: .seconds(fetchTimeoutSeconds))
                guard !Task.isCancelled else { return }
                guard let self, let pending = self.continuation else { return }
                #if DEBUG
                print("[LOCSHARE] fetch timed out after \(fetchTimeoutSeconds)s")
                #endif
                self.continuation = nil
                pending.resume(returning: nil)
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            fetchTimeoutTask?.cancel()
            continuation?.resume(returning: locations.last?.coordinate)
            continuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            #if DEBUG
            print("[LOCSHARE] fetch failed: \(error.localizedDescription)")
            #endif
            fetchTimeoutTask?.cancel()
            continuation?.resume(returning: nil)
            continuation = nil
        }
    }

    // MARK: - Throttle (onOpen mode only)

    /// Whether an "onOpen" write should proceed, or be skipped and why. Skips if
    /// the last write was under 15 minutes ago, or if the new fix is under ~0.5 mi
    /// from the last stored coordinate.
    func shouldWriteOnOpen(
        newCoordinate: CLLocationCoordinate2D,
        lastCoordinate: CLLocationCoordinate2D?,
        lastUpdatedAt: Date?
    ) -> (shouldWrite: Bool, skipReason: String?) {
        if let lastUpdatedAt, Date().timeIntervalSince(lastUpdatedAt) < throttleInterval {
            return (false, "throttled")
        }
        if let lastCoordinate {
            let movedMiles = CLLocation(latitude: newCoordinate.latitude, longitude: newCoordinate.longitude)
                .distance(from: CLLocation(latitude: lastCoordinate.latitude, longitude: lastCoordinate.longitude)) / 1609.34
            if movedMiles < minMovementMiles {
                return (false, "didn't move far enough")
            }
        }
        return (true, nil)
    }

    // MARK: - "When I open the app" trigger

    /// Called on every foreground transition, regardless of the user's current
    /// mode — logged unconditionally so a missed trigger is visible, per the
    /// on-device history of scenePhase alone silently failing to fire.
    func handleAppForeground() async {
        guard let userID = FirebaseAuthService.shared.currentUserID else {
            print("[LOCSHARE] onOpen trigger fired but no signed-in user — skipping")
            return
        }
        print("[LOCSHARE] onOpen trigger fired for uid=\(userID)")

        do {
            guard let user = try await FirestoreService.shared.fetchUser(userID: userID) else {
                print("[LOCSHARE] onOpen trigger — could not load user doc — skipping")
                return
            }
            guard user.locationSharingMode == LocationSharingMode.onOpen.rawValue else {
                print("[LOCSHARE] onOpen trigger — mode is \(user.locationSharingMode), not onOpen — skipping")
                return
            }

            refreshAuthorizationStatus()
            guard isAuthorized else {
                print("[LOCSHARE] onOpen trigger — permission not granted (\(authorizationStatus.debugLabel)) — skipping")
                return
            }

            guard let raw = await fetchRawLocationOnce() else {
                print("[LOCSHARE] onOpen trigger — fetch failed/timed out — keeping old location")
                return
            }

            let lastCoordinate: CLLocationCoordinate2D? =
                (user.latitude != 0 || user.longitude != 0)
                ? CLLocationCoordinate2D(latitude: user.latitude, longitude: user.longitude)
                : nil
            let (shouldWrite, skipReason) = shouldWriteOnOpen(
                newCoordinate: raw,
                lastCoordinate: lastCoordinate,
                lastUpdatedAt: user.locationUpdatedAt
            )
            guard shouldWrite else {
                print("[LOCSHARE] onOpen trigger — write skipped — reason=\(skipReason ?? "unknown")")
                return
            }

            let snapped = CoarseLocation.snap(raw)
            print("[LOCSHARE] onOpen trigger — writing raw=(\(raw.latitude), \(raw.longitude)) snapped=(\(snapped.latitude), \(snapped.longitude))")
            try await FirestoreService.shared.updateLocationSharing(
                userID: userID,
                mode: .onOpen,
                coordinate: snapped
            )
        } catch {
            print("[LOCSHARE] onOpen trigger — error: \(error.localizedDescription)")
        }
    }
}
