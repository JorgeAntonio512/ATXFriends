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
    
    /// Stores the FCM token to the user's Firestore document
    /// - Parameters:
    ///   - token: The FCM token to store
    ///   - userID: The Firebase UID of the user
    private func storeFCMToken(_ token: String, for userID: String) async {
        do {
            try await firestoreService.updateFCMToken(userID: userID, token: token)
            print("✅ FCM token stored to Firestore for user: \(userID)")
        } catch {
            print("❌ Error storing FCM token to Firestore: \(error.localizedDescription)")
        }
    }
    
    /// Removes the FCM token from the user's Firestore document (on sign out)
    /// - Parameter userID: The Firebase UID of the user
    func removeFCMToken(for userID: String) async {
        do {
            try await firestoreService.removeFCMToken(userID: userID)
            print("✅ FCM token removed from Firestore for user: \(userID)")
        } catch {
            print("❌ Error removing FCM token from Firestore: \(error.localizedDescription)")
        }
    }
    
    // MARK: - Badge Management
    
    /// Clears the app badge count
    @MainActor
    func clearBadge() {
        Task {
            do {
                try await UNUserNotificationCenter.current().setBadgeCount(0)
            } catch {
                print("❌ Error clearing badge: \(error.localizedDescription)")
            }
        }
    }
    
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
    
    /// Resets the unread count in Firestore and clears the app badge
    @MainActor
    func resetUnreadCount() {
        guard let userID = currentUserID else { return }
        clearBadge()
        Task {
            do {
                try await firestoreService.resetUnreadCount(userID: userID)
                print("✅ Unread count reset")
            } catch {
                print("❌ Error resetting unread count: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension NotificationManager: UNUserNotificationCenterDelegate {
    /// Called when a notification is received while the app is in the foreground
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        
        // Print notification info for debugging
        print("🔔 Notification received in foreground:")
        print(userInfo)
        
        // Show the notification even when app is in foreground
        completionHandler([.badge])
    }
    
    /// Called when the user taps on a notification
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        
        print("🔔 Notification tapped:")
        print(userInfo)
        
        // Handle notification tap based on type
        if let notificationType = userInfo["type"] as? String {
            Task { @MainActor in
                await self.handleNotificationTap(type: notificationType, data: userInfo)
            }
        }
        
        completionHandler()
    }
    
    /// Handles navigation when a notification is tapped
    /// - Parameters:
    ///   - type: The notification type
    ///   - data: Additional data from the notification
    private func handleNotificationTap(type: String, data: [AnyHashable: Any]) async {
        print("📱 Handling notification tap - Type: \(type)")
        
        // Post notification to navigate to the appropriate screen
        switch type {
        case "newMatch":
            if let matchId = data["matchId"] as? String {
                NotificationCenter.default.post(
                    name: .navigateToMatch,
                    object: nil,
                    userInfo: ["matchId": matchId]
                )
            }
            
        case "newMessage":
            if let senderId = data["senderId"] as? String {
                NotificationCenter.default.post(
                    name: .navigateToConversation,
                    object: nil,
                    userInfo: ["senderId": senderId]
                )
            }
            
        case "planRequest":
            if let planId = data["planId"] as? String {
                NotificationCenter.default.post(
                    name: .navigateToPlan,
                    object: nil,
                    userInfo: ["planId": planId]
                )
            }
            
        case "planConfirmed":
            if let planId = data["planId"] as? String {
                NotificationCenter.default.post(
                    name: .navigateToPlan,
                    object: nil,
                    userInfo: ["planId": planId]
                )
            }
            
        default:
            print("⚠️ Unknown notification type: \(type)")
        }
    }
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
    static let navigateToMatch = Notification.Name("navigateToMatch")
    static let navigateToConversation = Notification.Name("navigateToConversation")
    static let navigateToPlan = Notification.Name("navigateToPlan")
    static let onboardingCompleted = Notification.Name("onboardingCompleted")
}
