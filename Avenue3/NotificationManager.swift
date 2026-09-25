//
//  NotificationManager.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/24/26.
//

import Foundation
import UserNotifications
import FirebaseMessaging
import UIKit
import Combine

/// Manager for handling push notifications and Firebase Cloud Messaging
final class NotificationManager: NSObject, ObservableObject {
    // MARK: - Singleton
    
    static let shared = NotificationManager()
    
    // MARK: - Published State
    
    @Published var notificationPermissionStatus: UNAuthorizationStatus = .notDetermined
    @Published var fcmToken: String?
    @Published var hasRequestedPermission: Bool = false

    /// Set by a tap on a push notification (cold launch or backgrounded); consumed
    /// and cleared by MainTabView, which switches tabs and opens the target thread.
    @Published var pendingThreadRoute: PendingThreadRoute?

    /// The matchID of the thread currently on screen, if any. Set/cleared by
    /// MessageThreadView. Used by willPresent to suppress a banner for a push
    /// about the exact thread the user is already looking at.
    var currentlyOpenMatchID: String?

    // MARK: - Private Properties

    private let firestoreService = FirestoreService.shared
    private var currentUserID: String?
    
    // MARK: - Initialization
    
    private override init() {
        super.init()
        checkNotificationPermissionStatus()
        
        // Load permission request state from UserDefaults
        hasRequestedPermission = UserDefaults.standard.bool(forKey: "hasRequestedNotificationPermission")
    }
    
    // MARK: - Permission Management
    
    /// Checks the current notification permission status
    func checkNotificationPermissionStatus() {
        Task { @MainActor in
            let center = UNUserNotificationCenter.current()
            let settings = await center.notificationSettings()
            self.notificationPermissionStatus = settings.authorizationStatus
        }
    }
    
    /// Requests notification permissions from the user
    /// - Returns: True if permissions were granted, false otherwise
    @MainActor
    func requestNotificationPermission() async -> Bool {
        let center = UNUserNotificationCenter.current()
        
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            
            self.notificationPermissionStatus = granted ? .authorized : .denied
            self.hasRequestedPermission = true
            UserDefaults.standard.set(true, forKey: "hasRequestedNotificationPermission")
            
            if granted {
                // Register for remote notifications on the main thread
                registerForRemoteNotifications()
            }
            
            return granted
        } catch {
            print("❌ Error requesting notification permission: \(error.localizedDescription)")
            return false
        }
    }
    
    /// Registers the app for remote notifications with APNs
    @MainActor
    private func registerForRemoteNotifications() {
        UIApplication.shared.registerForRemoteNotifications()
    }
    
    // MARK: - FCM Token Management

    /// Sets the current user ID for FCM token storage
    /// - Parameter userID: The Firebase UID of the current user
    @MainActor
    func setCurrentUser(_ userID: String?) {
        self.currentUserID = userID

        // If we have a token and a user, store it immediately
        if let token = fcmToken, let userID = userID {
            Task {
                await storeFCMToken(token, for: userID)
            }
        }
    }

    /// Updates the FCM token
    /// - Parameter token: The new FCM token
    @MainActor
    func updateFCMToken(_ token: String) {
        self.fcmToken = token
        print("✅ FCM Token updated: \(token)")

        // Store the token to Firestore if we have a current user
        if let userID = currentUserID {
            Task {
                await storeFCMToken(token, for: userID)
            }
        }
    }

    /// Adds this device's token to the user's fcmTokens array in Firestore.
    /// - Parameters:
    ///   - token: The FCM token to store
    ///   - userID: The Firebase UID of the user
    private func storeFCMToken(_ token: String, for userID: String) async {
        do {
            try await firestoreService.addFCMToken(userID: userID, token: token)
            print("✅ FCM token stored to Firestore for user: \(userID)")
        } catch {
            print("❌ Error storing FCM token to Firestore: \(error.localizedDescription)")
        }
    }

    /// Removes exactly this device's token from the user's fcmTokens array
    /// (on sign out). This can never affect another device's or another
    /// account's token in the same array, unlike overwriting a single field.
    /// - Parameter userID: The Firebase UID of the user
    func removeFCMToken(for userID: String) async {
        guard let token = fcmToken else { return }
        do {
            try await firestoreService.removeFCMToken(userID: userID, token: token)
            print("✅ FCM token removed from Firestore for user: \(userID)")
        } catch {
            print("❌ Error removing FCM token from Firestore: \(error.localizedDescription)")
        }
    }

    /// Folds a legacy single `fcmToken` field into `fcmTokens`, if present.
    /// Call once per launch after sign-in; a no-op once already migrated.
    /// - Parameter userID: The Firebase UID of the user
    func migrateLegacyTokenIfNeeded(for userID: String) async {
        do {
            try await firestoreService.migrateLegacyFCMTokenIfNeeded(userID: userID)
        } catch {
            print("❌ Error migrating legacy FCM token: \(error.localizedDescription)")
        }
    }

    // MARK: - Badge Management

    /// Sets the app badge count
    /// - Parameter count: The badge number to display
    @MainActor
    func setBadge(count: Int) {
        Task {
            do {
                try await UNUserNotificationCenter.current().setBadgeCount(count)
            } catch {
                print("❌ Error setting badge count: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension NotificationManager: UNUserNotificationCenterDelegate {
    /// Called when a notification is received while the app is in the foreground.
    /// Shows a full banner/list/sound/badge — except when the push is about the
    /// exact thread the user is currently looking at (see currentlyOpenMatchID,
    /// set/cleared by MessageThreadView), in which case only the badge updates,
    /// since the message/plan itself will appear live in the open thread.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        print("🔔 Notification received in foreground:")
        print(userInfo)

        // Pull out only the Sendable value the Task actually needs — userInfo
        // itself ([AnyHashable: Any]) isn't Sendable and shouldn't cross into it.
        let matchID = userInfo["matchID"] as? String

        Task { @MainActor in
            if let matchID, matchID == self.currentlyOpenMatchID {
                completionHandler([.badge])
            } else {
                completionHandler([.banner, .list, .sound, .badge])
            }
        }
    }

    /// Called when the user taps on a notification (cold launch or backgrounded —
    /// this delegate method fires either way once the app finishes launching).
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo

        print("🔔 Notification tapped:")
        print(userInfo)

        // Pull out only the Sendable values the Task actually needs.
        let notificationType = userInfo["type"] as? String
        let matchID = userInfo["matchID"] as? String

        if let notificationType {
            Task { @MainActor in
                self.handleNotificationTap(type: notificationType, matchID: matchID)
            }
        }

        completionHandler()
    }

    /// Sets `pendingThreadRoute` from a tapped push. MainTabView observes this
    /// (both via `.onChange` for a live tap and via an `.onAppear`/`.task`
    /// check for cold launch, since the route may already be set before
    /// MainTabView's view tree exists), switches to the Messages tab, and
    /// opens the thread — falling back to the Matches tab for `newMatch` if
    /// no thread is found once the thread list finishes loading.
    /// - Parameters:
    ///   - type: The notification type
    ///   - matchID: The matchID from the notification payload, if any
    @MainActor
    private func handleNotificationTap(type: String, matchID: String?) {
        print("📱 Handling notification tap - Type: \(type)")

        switch type {
        case "newMatch":
            if let matchID {
                pendingThreadRoute = PendingThreadRoute(matchID: matchID, fallbackToMatchesTab: true)
            }

        case "newMessage", "planRequest", "planConfirmed", "planRescheduleRequested", "planRescheduleDeclined":
            if let matchID {
                pendingThreadRoute = PendingThreadRoute(matchID: matchID, fallbackToMatchesTab: false)
            }

        default:
            print("⚠️ Unknown notification type: \(type)")
        }
    }
}

// MARK: - Notification Tap Routing

/// A push tap's destination: open the thread for `matchID` once it's available
/// in the loaded thread list, or (only for a brand-new mutual match, where the
/// thread may not have loaded yet) fall back to the Matches tab if it never
/// shows up.
struct PendingThreadRoute: Equatable {
    let matchID: String
    let fallbackToMatchesTab: Bool
}

// MARK: - MessagingDelegate

extension NotificationManager: MessagingDelegate {
    /// Called when FCM registration token is updated
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("🔔 FCM Registration Token: \(fcmToken ?? "nil")")
        
        guard let fcmToken = fcmToken else { return }
        
        Task { @MainActor in
            self.updateFCMToken(fcmToken)
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let onboardingCompleted = Notification.Name("onboardingCompleted")
}
