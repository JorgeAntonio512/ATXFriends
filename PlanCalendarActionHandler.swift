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

/// A plan with a routable location — implemented by both the 1-on-1 `Plan` and the
/// multi-invitee `GroupPlan`, which store the same three location fields with the same
/// meaning. Lets `NavigationApp` and `CalendarEventFields` work off either without
/// duplicating logic per type.
protocol MapRoutable {
    var location: String? { get }
    var locationLatitude: Double? { get }
    var locationLongitude: Double? { get }
}

extension Plan: MapRoutable {}
extension GroupPlan: MapRoutable {}

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
    func directionsURL(for plan: MapRoutable) -> URL? {
        if let lat = plan.locationLatitude, let lng = plan.locationLongitude {
            return directionsURL(to: CLLocationCoordinate2D(latitude: lat, longitude: lng))
        } else if let location = plan.location, !location.isEmpty {
            return directionsURL(query: location)
        }
        return nil
    }
}

/// The fields every calendar provider actually needs, extracted once from either a 1-on-1
/// `Plan` or a `GroupPlan` so the Apple/Google/Microsoft event-creation code never needs to
/// branch on which source it came from.
struct CalendarEventFields {
    let title: String
    let start: Date
    let location: String?
    let notes: String

    var end: Date { start.addingTimeInterval(2 * 60 * 60) }

    /// Mirrors the exact title/start/location/notes mapping the 1-on-1 flow always used.
    init(plan: Plan, otherUserDisplayName: String) {
        self.title = plan.activity.name
        self.start = plan.confirmedDate ?? Date()
        self.location = plan.location
        self.notes = "Hanging out with \(otherUserDisplayName)"
    }

    init(groupPlan: GroupPlan, hostName: String, inviteeNames: [String]) {
        self.title = groupPlan.activity.name
        self.start = groupPlan.date
        self.location = groupPlan.location
        self.notes = "Hosted by \(hostName). Invited: \(inviteeNames.joined(separator: ", "))."
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

    func tap(provider: CalendarProvider, planID: String, fields: CalendarEventFields) {
        switch provider {
        case .apple:
            tapApple(planID: planID, fields: fields)
        case .google:
            tapGoogle(planID: planID, fields: fields)
        case .microsoft:
            tapMicrosoft(planID: planID, fields: fields)
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

    private func tapApple(planID: String, fields: CalendarEventFields) {
        if isAdded(.apple) {
            if let url = URL(string: "calshow:") {
                UIApplication.shared.open(url)
            }
            return
        }

        Task {
            let granted = await eventKitManager.requestWriteOnlyAccess()
            if granted {
                eventToAdd = Self.makeAppleEvent(for: fields, eventStore: eventKitManager.eventStore)
            } else {
                showCalendarPermissionAlert = true
            }
        }
    }

    private func tapGoogle(planID: String, fields: CalendarEventFields) {
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
                try await googleCalendarService.createEvent(for: fields, accessToken: accessToken)
                AddedToCalendarStore.markAdded(planID: planID, provider: .google)
                addedProviders.insert(.google)
            } catch {
                googleErrorMessage = error.localizedDescription
            }
        }
    }

    /// Microsoft has no "already connected" shortcut analogous to Google's `addScopes` branch —
    /// ATX Friends has no Microsoft login option, so there's never an existing MSAL session to
    /// extend. Every tap is a first-time interactive MSAL sign-in.
    private func tapMicrosoft(planID: String, fields: CalendarEventFields) {
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
                try await microsoftCalendarService.createEvent(for: fields, accessToken: accessToken)
                AddedToCalendarStore.markAdded(planID: planID, provider: .microsoft)
                addedProviders.insert(.microsoft)
            } catch {
                microsoftErrorMessage = error.localizedDescription
            }
        }
    }

    private static func makeAppleEvent(for fields: CalendarEventFields, eventStore: EKEventStore) -> EKEvent {
        let event = EKEvent(eventStore: eventStore)
        event.title = fields.title
        event.startDate = fields.start
        event.endDate = fields.end
        if let location = fields.location {
            event.location = location
        }
        event.notes = fields.notes
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
