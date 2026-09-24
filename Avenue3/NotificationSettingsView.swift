//
//  NotificationSettingsView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/24/26.
//

import SwiftUI
import UserNotifications
import FirebaseAuth

/// View for managing notification settings and permissions
struct NotificationSettingsView: View {
    @EnvironmentObject var notificationManager: NotificationManager
    @Environment(\.openURL) private var openURL
    
    @State private var currentUser: FirebaseUser?
    @State private var isLoading = true
    @State private var isSaving = false
    
    // Individual toggle states
    @State private var newMatchesEnabled = true
    @State private var newMessagesEnabled = true
    @State private var planRequestsEnabled = true
    @State private var planConfirmationsEnabled = true
    @State private var groupUpdatesEnabled = true
    
    var body: some View {
        ZStack {
            // Warm gradient background
            LinearGradient(
                colors: [
                    Color.appBackground,
                    Color.appBackground
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            if isLoading {
                ProgressView()
                    .tint(Color.appPrimary)
            } else {
                ScrollView {
                    VStack(spacing: 24) {
                        // Permission Status Card
                        VStack(spacing: 16) {
                            HStack(spacing: 12) {
                                Image(systemName: notificationIcon)
                                    .font(.system(size: 36))
                                    .foregroundColor(notificationColor)
                                
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Notification Status")
                                        .font(.system(size: 18, weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appTextStrong)
                                    
                                    Text(notificationStatusText)
                                        .font(.system(size: 15, weight: .medium, design: .rounded))
                                        .foregroundColor(notificationColor)
                                }
                                
                                Spacer()
                            }
                            .padding(20)
                            .background(Color.appCardBackground.opacity(0.7))
                            .cornerRadius(16)

                            // Permission-specific actions
                            if notificationManager.notificationPermissionStatus == .denied {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Notifications are disabled")
                                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appTextStrong)

                                    Text("To receive match updates, messages, and plan notifications, please enable notifications in Settings.")
                                        .font(.system(size: 14, weight: .regular, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                    
                                    Button {
                                        openAppSettings()
                                    } label: {
                                        HStack {
                                            Image(systemName: "gear")
                                            Text("Open Settings")
                                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        }
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 50)
                                        .background(
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary,
                                                    Color.appPrimary
                                                ],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                        )
                                        .cornerRadius(12)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(20)
                                .background(Color.appCardBackground.opacity(0.7))
                                .cornerRadius(16)
                            }

                            if notificationManager.notificationPermissionStatus == .notDetermined {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Enable Notifications")
                                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appTextStrong)

                                    Text("Get notified when you have new matches, messages, and plan confirmations.")
                                        .font(.system(size: 14, weight: .regular, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                    
                                    Button {
                                        Task {
                                            await notificationManager.requestNotificationPermission()
                                        }
                                    } label: {
                                        HStack {
                                            Image(systemName: "bell.badge")
                                            Text("Enable Notifications")
                                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                        }
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 50)
                                        .background(
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary,
                                                    Color.appPrimary
                                                ],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                        )
                                        .cornerRadius(12)
                                    }
                                    .buttonStyle(.plain)
                                }
                                .padding(20)
                                .background(Color.appCardBackground.opacity(0.7))
                                .cornerRadius(16)
                            }
                        }
                        .padding(.horizontal, 20)

                        // Notification Types
                        VStack(alignment: .leading, spacing: 12) {
                            Text("NOTIFICATION TYPES")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .padding(.horizontal, 24)
                            
                            VStack(spacing: 0) {
                                InteractiveNotificationToggleRow(
                                    icon: "person.2.fill",
                                    title: "New Matches",
                                    description: "When you match with someone new",
                                    isEnabled: $newMatchesEnabled,
                                    canToggle: notificationManager.notificationPermissionStatus == .authorized,
                                    onChange: { newValue in
                                        savePreferences()
                                    }
                                )
                                
                                Divider()
                                    .padding(.leading, 64)
                                
                                InteractiveNotificationToggleRow(
                                    icon: "message.fill",
                                    title: "Messages",
                                    description: "When you receive a new message",
                                    isEnabled: $newMessagesEnabled,
                                    canToggle: notificationManager.notificationPermissionStatus == .authorized,
                                    onChange: { newValue in
                                        savePreferences()
                                    }
                                )
                                
                                Divider()
                                    .padding(.leading, 64)
                                
                                InteractiveNotificationToggleRow(
                                    icon: "calendar.badge.plus",
                                    title: "Plan Requests",
                                    description: "When someone invites you to hang out",
                                    isEnabled: $planRequestsEnabled,
                                    canToggle: notificationManager.notificationPermissionStatus == .authorized,
                                    onChange: { newValue in
                                        savePreferences()
                                    }
                                )
                                
                                Divider()
                                    .padding(.leading, 64)
                                
                                InteractiveNotificationToggleRow(
                                    icon: "checkmark.circle.fill",
                                    title: "Plan Confirmations",
                                    description: "When a plan is confirmed",
                                    isEnabled: $planConfirmationsEnabled,
                                    canToggle: notificationManager.notificationPermissionStatus == .authorized,
                                    onChange: { newValue in
                                        savePreferences()
                                    }
                                )
                                
                                Divider()
                                    .padding(.leading, 64)
                                
                                InteractiveNotificationToggleRow(
                                    icon: "person.3.fill",
                                    title: "Group Updates",
                                    description: "Group join requests and membership changes",
                                    isEnabled: $groupUpdatesEnabled,
                                    canToggle: notificationManager.notificationPermissionStatus == .authorized,
                                    onChange: { newValue in
                                        savePreferences()
                                    }
                                )
                            }
                            .padding(20)
                            .background(Color.appCardBackground.opacity(0.7))
                            .cornerRadius(16)

                            Text("These notifications help you stay connected with your matches and never miss important updates.")
                                .font(.system(size: 12, weight: .regular, design: .rounded))
                                .foregroundColor(Color.appTextMuted)
                                .padding(.horizontal, 24)
                        }
                        .padding(.horizontal, 20)

                        #if DEBUG
                        // Debug Info
                        VStack(alignment: .leading, spacing: 12) {
                            Text("DEBUG INFO")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                                .padding(.horizontal, 24)

                            VStack(alignment: .leading, spacing: 12) {
                                if let token = notificationManager.fcmToken {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text("FCM Token")
                                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color.appTextBody)

                                        Text(token)
                                            .font(.system(size: 10, design: .monospaced))
                                            .foregroundColor(Color.appSecondaryText)
                                            .textSelection(.enabled)
                                    }
                                } else {
                                    Text("No FCM token yet")
                                        .font(.system(size: 12, weight: .regular, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                }
                            }
                            .padding(20)
                            .background(Color.appCardBackground.opacity(0.7))
                            .cornerRadius(16)
                        }
                        .padding(.horizontal, 20)
                        #endif
                    }
                    .padding(.top, 20)
                    .padding(.bottom, 40)
                }
                .scrollIndicators(.hidden)
            }
            
            // Saving indicator
            if isSaving {
                VStack {
                    Spacer()
                    HStack(spacing: 12) {
                        ProgressView()
                            .tint(.white)
                        
                        Text("Saving...")
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.appPrimary)
                    .cornerRadius(24)
                    .shadow(radius: 8)
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.large)
        .task {
            await loadUserPreferences()
            notificationManager.checkNotificationPermissionStatus()
        }
    }
    
    // MARK: - Computed Properties
    
    private var notificationIcon: String {
        switch notificationManager.notificationPermissionStatus {
        case .authorized, .provisional, .ephemeral:
            return "bell.badge.fill"
        case .denied:
            return "bell.slash.fill"
        case .notDetermined:
            return "bell.fill"
        @unknown default:
            return "bell.fill"
        }
    }
    
    private var notificationColor: Color {
        switch notificationManager.notificationPermissionStatus {
        case .authorized, .provisional, .ephemeral:
            return Color.appPrimary
        case .denied:
            return Color.appDanger
        case .notDetermined:
            return Color.orange
        @unknown default:
            return Color.gray
        }
    }
    
    private var notificationStatusText: String {
        switch notificationManager.notificationPermissionStatus {
        case .authorized:
            return "Enabled"
        case .denied:
            return "Disabled"
        case .notDetermined:
            return "Not Set"
        case .provisional:
            return "Provisional"
        case .ephemeral:
            return "Ephemeral"
        @unknown default:
            return "Unknown"
        }
    }
    
    // MARK: - Methods
    
    /// Loads user notification preferences from Firestore
    private func loadUserPreferences() async {
        guard let userID = Auth.auth().currentUser?.uid else {
            await MainActor.run { isLoading = false }
            return
        }
        
        do {
            if let user = try await FirestoreService.shared.fetchUser(userID: userID) {
                await MainActor.run {
                    self.currentUser = user
                    self.newMatchesEnabled = user.notificationPreferences.newMatches
                    self.newMessagesEnabled = user.notificationPreferences.newMessages
                    self.planRequestsEnabled = user.notificationPreferences.planRequests
                    self.planConfirmationsEnabled = user.notificationPreferences.planConfirmations
                    self.groupUpdatesEnabled = user.notificationPreferences.groupUpdates
                    self.isLoading = false
                }
            }
        } catch {
            print("❌ Error loading notification preferences: \(error.localizedDescription)")
            await MainActor.run { isLoading = false }
        }
    }
    
    /// Saves notification preferences to Firestore
    private func savePreferences() {
        guard let userID = Auth.auth().currentUser?.uid else { return }
        
        isSaving = true
        
        Task {
            let preferences = NotificationPreferences(
                newMatches: newMatchesEnabled,
                newMessages: newMessagesEnabled,
                planRequests: planRequestsEnabled,
                planConfirmations: planConfirmationsEnabled,
                groupUpdates: groupUpdatesEnabled
            )
            
            do {
                try await FirestoreService.shared.updateNotificationPreferences(
                    userID: userID,
                    preferences: preferences
                )
                
                // Update local user object
                await MainActor.run {
                    currentUser?.notificationPreferences = preferences
                }
                
                // Small delay to show saving indicator
                try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
                
                await MainActor.run {
                    isSaving = false
                }
                
                print("✅ Notification preferences saved")
            } catch {
                print("❌ Error saving notification preferences: \(error.localizedDescription)")
                await MainActor.run {
                    isSaving = false
                }
            }
        }
    }
    
    private func openAppSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            openURL(url)
        }
    }
}

// MARK: - Interactive Notification Toggle Row

struct InteractiveNotificationToggleRow: View {
    let icon: String
    let title: String
    let description: String
    @Binding var isEnabled: Bool
    let canToggle: Bool
    let onChange: (Bool) -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundColor(canToggle ? Color.appPrimary : Color.gray.opacity(0.5))
                .frame(width: 40)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(canToggle ? Color.appTextStrong : Color.gray.opacity(0.7))

                Text(description)
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
            
            Spacer()
            
            Toggle("", isOn: $isEnabled)
                .labelsHidden()
                .tint(Color.appPrimary)
                .disabled(!canToggle)
                .onChange(of: isEnabled) { oldValue, newValue in
                    onChange(newValue)
                }
        }
        .padding(.vertical, 8)
        .opacity(canToggle ? 1.0 : 0.6)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        NotificationSettingsView()
            .environmentObject(NotificationManager.shared)
    }
}
