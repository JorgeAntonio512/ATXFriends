//
//  AppDelegate.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/24/26.
//

import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications

/// AppDelegate for handling Firebase Cloud Messaging and push notifications
class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        
        // Set up notification center delegate
        UNUserNotificationCenter.current().delegate = NotificationManager.shared
        
        // Set up FCM messaging delegate
        Messaging.messaging().delegate = NotificationManager.shared
        
        return true
    }
    
    // MARK: - Remote Notification Registration
    
    /// Called when APNs registration succeeds
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        print("✅ APNs Device Token received")
        
        // Pass the device token to FCM
        Messaging.messaging().apnsToken = deviceToken
    }
    
    /// Called when APNs registration fails
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("❌ Failed to register for remote notifications: \(error.localizedDescription)")
    }
}
