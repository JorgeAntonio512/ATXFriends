//
//  EventKitPermissionManager.swift
//  Avenue3
//

import Combine
import EventKit

extension EKAuthorizationStatus {
    /// Human-readable label for logging.
    var debugLabel: String {
        switch self {
        case .notDetermined: return "notDetermined"
        case .restricted: return "restricted"
        case .denied: return "denied"
        case .fullAccess: return "fullAccess"
        case .writeOnly: return "writeOnly"
        @unknown default: return "unknown(\(rawValue))"
        }
    }
}

@MainActor
final class EventKitPermissionManager: NSObject, ObservableObject {
    @Published var authorizationStatus: EKAuthorizationStatus

    /// Shared across all calendar operations for this manager's lifetime, per Apple's
    /// guidance that EKEventStore is expensive to init and should be reused.
    let eventStore = EKEventStore()

    override init() {
        self.authorizationStatus = EKEventStore.authorizationStatus(for: .event)
        super.init()
    }

    /// True once the app can create events without prompting again.
    var hasWriteAccess: Bool {
        authorizationStatus == .writeOnly || authorizationStatus == .fullAccess
    }

    /// Re-reads authorization status directly from EKEventStore — e.g. after the user
    /// returns from Settings having changed calendar access there.
    func refreshAuthorizationStatus() {
        authorizationStatus = EKEventStore.authorizationStatus(for: .event)
    }

    /// Requests write-only access to events if not yet determined.
    /// Returns whether the app can proceed to create an event.
    func requestWriteOnlyAccess() async -> Bool {
        let currentStatus = EKEventStore.authorizationStatus(for: .event)

        guard currentStatus == .notDetermined else {
            authorizationStatus = currentStatus
            return currentStatus == .writeOnly || currentStatus == .fullAccess
        }

        do {
            let granted = try await eventStore.requestWriteOnlyAccessToEvents()
            authorizationStatus = EKEventStore.authorizationStatus(for: .event)
            return granted
        } catch {
            print("❌ [EventKitPermissionManager] requestWriteOnlyAccessToEvents failed: \(error)")
            authorizationStatus = EKEventStore.authorizationStatus(for: .event)
            return false
        }
    }
}
