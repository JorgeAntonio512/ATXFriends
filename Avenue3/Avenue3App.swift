//
//  Avenue3App.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/5/26.
//

import SwiftUI
import SwiftData
import FirebaseCore
import FirebaseAuth
import GoogleSignIn
import MSAL

@main
struct Avenue3App: App {
    // AppDelegate for handling push notifications
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    // SwiftData model container
    let modelContainer = Avenue3ModelConfiguration.createContainer()
    
    // Launch screen state
    @State private var showLaunchScreen = true
    
    // Notification manager
    @StateObject private var notificationManager = NotificationManager.shared
    
    // Track auth state to manage FCM token
    @State private var currentUserID: String?

    // Drives the "When I open the app" location-sharing trigger below
    @Environment(\.scenePhase) private var scenePhase

    // Firebase initialization
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                    .opacity(showLaunchScreen ? 0 : 1)

                if showLaunchScreen {
                    LaunchScreenView()
                        .transition(.opacity)
                }
            }
            .environmentObject(notificationManager)
            .onChange(of: scenePhase) { oldPhase, newPhase in
                // Logged unconditionally, regardless of the user's sharing mode —
                // scenePhase alone has silently failed to fire on-device before,
                // so a missed trigger needs to be visible here, not just inside
                // LocationSharingManager.
                print("[LOCSHARE] app scenePhase: \(oldPhase) -> \(newPhase)")
                guard newPhase == .active else { return }
                Task { await LocationSharingManager.shared.handleAppForeground() }
            }
            .onAppear {
                // Hide launch screen after animation
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    withAnimation(.easeOut(duration: 0.5)) {
                        showLaunchScreen = false
                    }
                }

                // Restores GIDSignIn's own Keychain-backed session (if any) so
                // GIDSignIn.sharedInstance.currentUser is populated on relaunch —
                // e.g. for the Google Calendar connection made from a plan's
                // "Add to Calendar" menu. This is entirely separate from Firebase
                // Auth's own session restoration and never affects it.
                GIDSignIn.sharedInstance.restorePreviousSignIn { _, _ in }
            }
            .onOpenURL { url in
                // Handle Google Sign In URL callback
                GIDSignIn.sharedInstance.handle(url)
                // Handle MSAL (Microsoft/Outlook Calendar) URL callback. Unlike modern
                // GoogleSignIn, MSAL does not handle its redirect implicitly — per Microsoft's
                // own docs, this call is required or the interactive sign-in flow never
                // completes. Each incoming URL only matches one SDK's registered scheme; the
                // other call is a harmless no-op for it.
                MSALPublicClientApplication.handleMSALResponse(url, sourceApplication: nil)
            }
            .onReceive(NotificationCenter.default.publisher(for: .authStateDidChange)) { _ in
                handleAuthStateChange()
            }
        }
        .modelContainer(modelContainer)
    }
    
    // MARK: - Auth State Management
    
    /// Handles authentication state changes to manage FCM tokens
    private func handleAuthStateChange() {
        Task { @MainActor in
            // Get the current user ID from Firebase Auth
            if let userID = FirebaseAuth.Auth.auth().currentUser?.uid {
                // User signed in - set the current user for notification manager
                currentUserID = userID
                notificationManager.setCurrentUser(userID)
                UnreadState.shared.startListening(userID: userID)  // ← ADD THIS
                Task { await notificationManager.migrateLegacyTokenIfNeeded(for: userID) }

                print("✅ User signed in - FCM token will be stored for: \(userID)")
            } else {
                // User signed out - remove FCM token
                if let userID = currentUserID {
                    await notificationManager.removeFCMToken(for: userID)
                }
                currentUserID = nil
                notificationManager.setCurrentUser(nil)
                UnreadState.shared.stopListening()                  // ← ADD THIS
                
                print("✅ User signed out - FCM token removed")
            }
        }
    }
}
