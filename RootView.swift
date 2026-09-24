//
//  RootView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

/// Root view that manages authentication state and navigation
/// Routes to OnboardingView if not authenticated, ProfileSetupFlowView if authenticated but incomplete profile, or MainTabView if fully set up
struct RootView: View {
    @State private var authViewModel = AuthViewModel()
    @State private var isCheckingAuth = true
    @State private var isProfileComplete = false
    @State private var authStateRefreshTrigger = 0 // Used to force re-checks
    @State private var isCheckingProfile = false // Prevents concurrent profile checks
    
    var body: some View {
        ZStack {
            if isCheckingAuth {
                // Loading state while checking auth
                LoadingView()
            } else if authViewModel.authState.isAuthenticated {
                if let pending = authViewModel.pendingNewSSOUser {
                    // New SSO user: Firebase Auth created but no Firestore doc yet.
                    // Show the location gate; gate pass creates the doc with real coordinates.
                    NavigationStack {
                        LocationGateView(
                            path: pending.provider,
                            onDismissAll: {
                                Task { await authViewModel.cancelNewSSOSignup() }
                            },
                            onGatePass: { coord in
                                Task { _ = await authViewModel.createNewSSOUser(coordinate: coord) }
                            }
                        )
                    }
                    .transition(.opacity)
                    .alert(
                        "Account Error",
                        isPresented: Binding(
                            get: { authViewModel.pendingNewSSOUser != nil && authViewModel.errorMessage != nil },
                            set: { if !$0 { authViewModel.clearError() } }
                        )
                    ) {
                        Button("Try Again") { authViewModel.clearError() }
                        Button("Cancel", role: .destructive) {
                            Task { await authViewModel.cancelNewSSOSignup() }
                        }
                    } message: {
                        Text(authViewModel.errorMessage ?? "Failed to create account.")
                    }
                } else if isProfileComplete {
                    // Profile complete - show main app
                    MainTabView()
                        .transition(.opacity)
                } else {
                    // Profile incomplete - show setup flow
                    ProfileSetupFlowView()
                        .transition(.opacity)
                }
            } else {
                // User is not signed in - show onboarding.
                // Inject this single AuthViewModel instance into the environment so
                // OnboardingView/SignInView/SignUpView all mutate the SAME pendingNewSSOUser
                // this view is watching — otherwise a new SSO user's pendingNewSSOUser gets
                // set on a throwaway local instance, and RootView only discovers it
                // asynchronously via the orphan-recovery check below, leaving a race window
                // where ProfileSetupFlowView can render before the location gate.
                OnboardingView()
                    .environment(authViewModel)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: authViewModel.authState)
        .animation(.easeInOut(duration: 0.3), value: isProfileComplete)
        .task {
            // Check authentication state on appear
            await checkAuthState()
        }
        .onChange(of: authViewModel.authState) { _, newState in
            // When auth state changes, check profile completion
            print("🔄 RootView: Auth state changed to \(newState)")
            if newState.isAuthenticated {
                Task {
                    await checkProfileCompletion()
                }
            } else {
                isProfileComplete = false
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .authStateDidChange)) { _ in
            // Refresh auth state when notification is received
            print("🔔 RootView: Received auth state change notification")
            Task {
                await refreshAuthState()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .userSignedOut)) { _ in
            // Handle user sign out (e.g., from account deletion)
            print("🔔 RootView: Received userSignedOut notification")
            Task {
                await refreshAuthState()
            }
        }
    }
    
    /// Checks the current authentication state
    private func checkAuthState() async {
        print("🔍 RootView: checkAuthState called")
        // Small delay to avoid flashing loading screen
        try? await Task.sleep(nanoseconds: 300_000_000) // 0.3 seconds
        
        await refreshAuthState()
        
        isCheckingAuth = false
    }
    
    /// Refreshes the auth state from Firebase
    private func refreshAuthState() async {
        let oldState = authViewModel.authState
        authViewModel.refreshAuthState()
        let newState = authViewModel.authState
        
        print("🔄 RootView: refreshAuthState - Old: \(oldState), New: \(newState)")
        
        // Always check profile completion if authenticated
        // (not just when state changes, since profile completion can change too)
        if newState.isAuthenticated {
            await checkProfileCompletion()
        }
    }
    
    /// Checks if the user's profile is complete
    private func checkProfileCompletion() async {
        // Prevent concurrent checks
        guard !isCheckingProfile else {
            print("⏭️ RootView: Skipping profile check - already in progress")
            return
        }
        
        guard let userID = authViewModel.currentUserID else {
            isProfileComplete = false
            return
        }
        
        isCheckingProfile = true
        defer { isCheckingProfile = false }
        
        do {
            let firestoreService = FirestoreService.shared
            let user = try await firestoreService.fetchUser(userID: userID)

            if user == nil && authViewModel.pendingNewSSOUser == nil {
                // Authenticated but no Firestore doc — this is an orphaned SSO signup
                // (app was killed after Apple/Google auth succeeded but before the location
                // gate completed). Re-derive the pending state and route back to the gate.
                print("⚠️ RootView: Authenticated with no Firestore doc — routing to location gate")
                logOnboarding(path: "resume", step: "locationGate", gate: .notRun)
                authViewModel.pendingNewSSOUser = PendingNewSSOUser(
                    userID: userID,
                    displayName: authViewModel.currentUserDisplayName,
                    provider: "sso"
                )
                return
            }

            if let user {
                Task { await LocationRepairService.shared.repairIfNeeded(for: user) }
            }

            let newStatus = user?.isProfileComplete ?? false

            // Only update if changed to avoid unnecessary view updates
            if isProfileComplete != newStatus {
                isProfileComplete = newStatus
                print("✅ RootView: Profile complete status changed to: \(isProfileComplete)")
                logOnboarding(path: "resume", step: newStatus ? "mainTab" : "profileSetup", gate: .passed)
            }
        } catch {
            print("❌ RootView: Error checking profile completion: \(error)")
            isProfileComplete = false
        }
    }
}

// MARK: - Notification Names

extension Notification.Name {
    static let authStateDidChange = Notification.Name("authStateDidChange")
    static let userSignedOut = Notification.Name("userSignedOut")
}

/// Simple loading view shown while checking auth state
struct LoadingView: View {
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
            
            VStack(spacing: 24) {
                // Logo
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.appPrimary,
                                    Color.appPrimary
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 100, height: 100)
                        .shadow(color: .black.opacity(0.1), radius: 20, x: 0, y: 10)
                    
                    Image(systemName: "heart.text.square.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.white)
                }
                
                // App name
                Text("ATX Friends")
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [
                                Color.appPrimaryText,
                                Color.appPrimary
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                
                // Loading indicator
                ProgressView()
                    .tint(Color.appPrimary)
                    .scaleEffect(1.2)
            }
        }
    }
}

#Preview("Root View - Loading") {
    RootView()
}

#Preview("Loading View") {
    LoadingView()
}
