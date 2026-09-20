//
//  PlanCalendarActionHandler.swift
//  Avenue3
//

import CoreLocation
import EventKit
import EventKitUI
import UIKit

/// A calendar a confirmed plan can be added to.
enum CalendarProvider: String, CaseIterable, Identifiable {
    case apple
    case google
    case microsoft

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .apple: return "Apple Calendar"
        case .google: return "Google Calendar"
        case .microsoft: return "Outlook Calendar"
        }
    }
}

/// A maps app that can be launched with directions to a plan's location. Apple Maps is the
/// guaranteed system fallback; Google Maps and Waze are offered only when actually installed.
enum NavigationApp: String, CaseIterable, Identifiable {
    case apple
    case google
    case waze

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .apple: return "Apple Maps"
        case .google: return "Google Maps"
        case .waze: return "Waze"
        }
    }

    /// URL scheme probed to detect installation. Apple Maps has none — it's a system app,
    /// always available.
    private var probeURL: URL? {
        switch self {
        case .apple: return nil
        case .google: return URL(string: "comgooglemaps://")
        case .waze: return URL(string: "waze://")
        }
    }

    var isAvailable: Bool {
        guard let probeURL else { return true }
        return UIApplication.shared.canOpenURL(probeURL)
    }

    /// Only the apps actually installed on this device.
    static var availableApps: [NavigationApp] {
        allCases.filter { $0.isAvailable }
    }

    /// Directions URL using exact coordinates.
    private func directionsURL(to coordinate: CLLocationCoordinate2D) -> URL? {
        switch self {
        case .apple:
            return URL(string: "http://maps.apple.com/?daddr=\(coordinate.latitude),\(coordinate.longitude)")
        case .google:
            return URL(string: "comgooglemaps://?daddr=\(coordinate.latitude),\(coordinate.longitude)&directionsmode=driving")
        case .waze:
            return URL(string: "waze://?ll=\(coordinate.latitude),\(coordinate.longitude)&navigate=yes")
        }
    }

    /// Directions URL using a free-text query — fallback for plans that predate this feature
    /// and have no stored coordinates.
    private func directionsURL(query: String) -> URL? {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        switch self {
        case .apple:
            return URL(string: "http://maps.apple.com/?daddr=\(encoded)")
        case .google:
            return URL(string: "comgooglemaps://?daddr=\(encoded)&directionsmode=driving")
        case .waze:
            return URL(string: "waze://?q=\(encoded)")
        }
    }

    /// Best-available directions URL for a plan: exact coordinates when on file, otherwise a
    /// free-text query using the display string.
    func directionsURL(for plan: Plan) -> URL? {
        if let lat = plan.locationLatitude, let lng = plan.locationLongitude {
            return directionsURL(to: CLLocationCoordinate2D(latitude: lat, longitude: lng))
        } else if let location = plan.location, !location.isEmpty {
            return directionsURL(query: location)
        }
        return nil
    }
}

/// Tracks which plans have already had a calendar event created, per-device, per-provider.
/// Local-only by design: EKEvent identifiers and Google Calendar event IDs aren't guaranteed
/// stable across devices or resyncs, so this is intentionally never written to Firestore.
enum AddedToCalendarStore {
    private static func key(for planID: String, provider: CalendarProvider) -> String {
        "addedToCalendar.\(provider.rawValue).\(planID)"
    }

    static func hasAdded(planID: String, provider: CalendarProvider) -> Bool {
        UserDefaults.standard.bool(forKey: key(for: planID, provider: provider))
    }

    static func markAdded(planID: String, provider: CalendarProvider) {
        UserDefaults.standard.set(true, forKey: key(for: planID, provider: provider))
    }
}

/// Shared logic for adding a confirmed `Plan` to a calendar provider (Apple EventKit or
/// Google Calendar). Reused by the Messages-thread pinned-plan banner (`PinnedPlanCard`
/// in MessageThreadView) and `PlanDetailView` so the two entry points can't drift apart.
@Observable
final class PlanCalendarActionHandler {
    private let eventKitManager = EventKitPermissionManager()
    private let googleCalendarService = GoogleCalendarService()
    private let microsoftCalendarService = MicrosoftCalendarService()

    var eventToAdd: EKEvent?
    var addedProviders: Set<CalendarProvider> = []
    var showCalendarPermissionAlert = false
    var googleErrorMessage: String?
    var isAuthorizingGoogle = false
    var microsoftErrorMessage: String?
    var isAuthorizingMicrosoft = false

    var eventStore: EKEventStore { eventKitManager.eventStore }

    func isAdded(_ provider: CalendarProvider) -> Bool {
        addedProviders.contains(provider)
    }

    /// Re-reads per-provider "already added" state for a plan — call on appear and
    /// whenever the pinned/displayed plan changes.
    func loadAddedState(planID: String) {
        addedProviders = Set(CalendarProvider.allCases.filter {
            AddedToCalendarStore.hasAdded(planID: planID, provider: $0)
        })
    }

    func tap(provider: CalendarProvider, plan: Plan, otherUserDisplayName: String) {
        switch provider {
        case .apple:
            tapApple(plan: plan, otherUserDisplayName: otherUserDisplayName)
        case .google:
            tapGoogle(plan: plan, otherUserDisplayName: otherUserDisplayName)
        case .microsoft:
            tapMicrosoft(plan: plan, otherUserDisplayName: otherUserDisplayName)
        }
    }

    /// Call from the `EKEventEditViewController` completion once the Apple sheet is dismissed.
    func handleAppleEventEditCompletion(_ action: EKEventEditViewAction, planID: String) {
        if action == .saved {
            AddedToCalendarStore.markAdded(planID: planID, provider: .apple)
            addedProviders.insert(.apple)
        }
        eventToAdd = nil
    }

    private func tapApple(plan: Plan, otherUserDisplayName: String) {
        if isAdded(.apple) {
            if let url = URL(string: "calshow:") {
                UIApplication.shared.open(url)
            }
            return
        }

        Task {
            let granted = await eventKitManager.requestWriteOnlyAccess()
            if granted {
                eventToAdd = Self.makeAppleEvent(
                    for: plan,
                    eventStore: eventKitManager.eventStore,
                    otherUserDisplayName: otherUserDisplayName
                )
            } else {
                showCalendarPermissionAlert = true
            }
        }
    }

    private func tapGoogle(plan: Plan, otherUserDisplayName: String) {
        if isAdded(.google) {
            if let url = URL(string: "https://calendar.google.com/calendar/r") {
                UIApplication.shared.open(url)
            }
            return
        }

        guard let presentingViewController = Self.currentRootViewController() else {
            googleErrorMessage = "Couldn't find a screen to present Google Sign-In from."
            return
        }

        isAuthorizingGoogle = true
        Task {
            defer { isAuthorizingGoogle = false }
            do {
                let accessToken = try await googleCalendarService.authorize(presenting: presentingViewController)
                try await googleCalendarService.createEvent(
                    for: plan,
                    otherUserDisplayName: otherUserDisplayName,
                    accessToken: accessToken
                )
                AddedToCalendarStore.markAdded(planID: plan.id, provider: .google)
                addedProviders.insert(.google)
            } catch {
                googleErrorMessage = error.localizedDescription
            }
        }
    }

    /// Microsoft has no "already connected" shortcut analogous to Google's `addScopes` branch —
    /// ATX Friends has no Microsoft login option, so there's never an existing MSAL session to
    /// extend. Every tap is a first-time interactive MSAL sign-in.
    private func tapMicrosoft(plan: Plan, otherUserDisplayName: String) {
        if isAdded(.microsoft) {
            if let url = URL(string: "https://outlook.office.com/calendar/view/month") {
                UIApplication.shared.open(url)
            }
            return
        }

        guard let presentingViewController = Self.currentRootViewController() else {
            microsoftErrorMessage = "Couldn't find a screen to present Microsoft Sign-In from."
            return
        }

        isAuthorizingMicrosoft = true
        Task {
            defer { isAuthorizingMicrosoft = false }
            do {
                let accessToken = try await microsoftCalendarService.authorize(presenting: presentingViewController)
                try await microsoftCalendarService.createEvent(
                    for: plan,
                    otherUserDisplayName: otherUserDisplayName,
                    accessToken: accessToken
                )
                AddedToCalendarStore.markAdded(planID: plan.id, provider: .microsoft)
                addedProviders.insert(.microsoft)
            } catch {
                microsoftErrorMessage = error.localizedDescription
            }
        }
    }

    private static func makeAppleEvent(for plan: Plan, eventStore: EKEventStore, otherUserDisplayName: String) -> EKEvent {
        let event = EKEvent(eventStore: eventStore)
        event.title = plan.activity.name
        let start = plan.confirmedDate ?? Date()
        event.startDate = start
        event.endDate = start.addingTimeInterval(2 * 60 * 60)
        if let location = plan.location {
            event.location = location
        }
        event.notes = "Hanging out with \(otherUserDisplayName)"
        return event
    }

    private static func currentRootViewController() -> UIViewController? {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            return nil
        }
        return rootViewController
    }
}
