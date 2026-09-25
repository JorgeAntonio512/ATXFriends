//
//  AuthViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftUI
import CoreLocation
import AuthenticationServices
import FirebaseAuth
import FirebaseFirestore

/// ViewModel for authentication and user session management
/// Handles sign up, sign in, sign out, and auth state monitoring
@Observable
final class AuthViewModel {
    // MARK: - Published State
    
    /// Current authentication state
    var authState: AuthState = .unauthenticated
    
    /// The currently authenticated user's ID
    var currentUserID: String?
    
    /// Error message to display to the user
    var errorMessage: String?

    /// True when the current errorMessage is the combined "wrong password or unknown
    /// email" case — the sign-in screen uses this to offer a "Create an account" link.
    var showCreateAccountPrompt: Bool = false
    
    /// Whether an auth operation is in progress
    var isLoading: Bool = false

    /// Set for newly authenticated SSO users while they complete the location gate.
    /// The Firestore user doc is created only after the gate passes.
    var pendingNewSSOUser: PendingNewSSOUser?

    // MARK: - Services
    
    private let authService = FirebaseAuthService.shared
    private let firestoreService = FirestoreService.shared
    
    // MARK: - Initialization
    
    init() {
        // Observe auth state changes
        observeAuthState()
    }
    
    // MARK: - Auth State Monitoring
    
    /// Sets up observation of authentication state changes
    private func observeAuthState() {
        // The FirebaseAuthService already monitors auth state
        // We just need to sync our local state
        currentUserID = authService.currentUserID
        authState = authService.isAuthenticated ? .authenticated : .unauthenticated
    }
    
    /// Refreshes the current auth state
    func refreshAuthState() {
        currentUserID = authService.currentUserID
        authState = authService.isAuthenticated ? .authenticated : .unauthenticated
    }
    
    // MARK: - Sign Up
    
    /// Signs up a new user with email and password
    /// - Parameters:
    ///   - email: User's email address
    ///   - password: User's password
    ///   - confirmPassword: Password confirmation
    /// - Returns: True if successful, false otherwise
    @MainActor
    func signUp(email: String, password: String, confirmPassword: String, coordinate: CLLocationCoordinate2D) async -> Bool {
        print("🟢 AuthViewModel.signUp called")
        print("   Email: \(email)")
        print("   Password length: \(password.count)")
        print("   Confirm password length: \(confirmPassword.count)")
        
        // Clear previous errors
        errorMessage = nil
        showCreateAccountPrompt = false
        isLoading = true
        defer { 
            isLoading = false
            print("🟢 AuthViewModel.signUp: isLoading set to false")
        }
        
        // Validate inputs
        print("🟢 AuthViewModel.signUp: Validating inputs...")
        guard validateSignUp(email: email, password: password, confirmPassword: confirmPassword) else {
            print("❌ AuthViewModel.signUp: Validation failed - \(errorMessage ?? "no error")")
            return false
        }
        
        print("✅ AuthViewModel.signUp: Validation passed")
        logOnboarding(path: "email", step: "signUp", gate: .passed)

        do {
            // Create user account
            print("🟢 AuthViewModel.signUp: Calling authService.signUp...")
            let userID = try await authService.signUp(email: email, password: password)
            print("✅ AuthViewModel.signUp: Firebase user created - ID: \(userID)")
            
            // Create initial user document in Firestore with incomplete profile
            print("🟢 AuthViewModel.signUp: Creating Firestore user document...")
            let user = FirebaseUser(
                id: userID,
                displayName: "",
                photoURLs: [],
                activities: [],
                daySlotCombos: [],
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                radiusMiles: 10.0,
                isProfileComplete: false
            )

            try await firestoreService.createUser(user)
            print("✅ AuthViewModel.signUp: Firestore user document created")
            
            // Update local state
            currentUserID = userID
            authState = .authenticated
            
            // Notify that auth state changed
            NotificationCenter.default.post(name: .authStateDidChange, object: nil)
            
            print("✅ AuthViewModel.signUp: Complete! User ID: \(userID)")
            print("   Auth state: \(authState)")
            
            return true
        } catch {
            print("❌ AuthViewModel.signUp: Error occurred - \(error.localizedDescription)")
            print("   Error details: \(error)")
            let presentation = AuthErrorMapper.presentation(for: error)
            errorMessage = presentation.message
            showCreateAccountPrompt = false
            return false
        }
    }
    
    // MARK: - Sign In
    
    /// Signs in an existing user with email and password
    /// - Parameters:
    ///   - email: User's email address
    ///   - password: User's password
    /// - Returns: True if successful, false otherwise
    @MainActor
    func signIn(email: String, password: String) async -> Bool {
        // Clear previous errors
        errorMessage = nil
        showCreateAccountPrompt = false
        isLoading = true
        defer { isLoading = false }
        
        // Validate inputs
        guard validateSignIn(email: email, password: password) else {
            return false
        }
        
        do {
            // Sign in user
            let userID = try await authService.signIn(email: email, password: password)
            
            // Update local state
            currentUserID = userID
            authState = .authenticated
            
            // Notify that auth state changed
            NotificationCenter.default.post(name: .authStateDidChange, object: nil)
            
            return true
        } catch {
            let presentation = AuthErrorMapper.presentation(for: error)
            errorMessage = presentation.message
            showCreateAccountPrompt = presentation.suggestsAccountCreation
            return false
        }
    }
    
    // MARK: - Sign in with Apple
    
    /// Prepares for Sign in with Apple
    /// - Returns: The nonce to use in the Apple sign-in request
    func prepareAppleSignIn() -> String {
        return authService.prepareAppleSignIn()
    }
    
    /// Handles Sign in with Apple authorization
    /// - Parameter authorization: The authorization from Apple
    /// - Returns: True if successful, false otherwise
    @MainActor
    func handleAppleSignIn(_ authorization: ASAuthorization) async -> Bool {
        // Clear previous errors
        errorMessage = nil
        showCreateAccountPrompt = false
        isLoading = true
        defer { isLoading = false }
        
        guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            errorMessage = "Invalid Apple ID credential."
            return false
        }
        
        do {
            // Sign in with Apple credential
            let (userID, isNewUser) = try await authService.signInWithApple(credential: appleIDCredential)
            
            if isNewUser {
                // Get display name from Apple (if provided)
                var displayName = ""
                if let fullName = appleIDCredential.fullName {
                    let firstName = fullName.givenName ?? ""
                    let lastName = fullName.familyName ?? ""
                    displayName = "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
                }

                // Defer Firestore doc creation until the location gate passes
                // (RootView will show LocationGateView while pendingNewSSOUser is set).
                pendingNewSSOUser = PendingNewSSOUser(userID: userID, displayName: displayName, provider: "apple")
                logOnboarding(path: "apple", step: "locationGate", gate: .notRun)
            }

            // Update local state
            currentUserID = userID
            authState = .authenticated

            // Notify that auth state changed
            NotificationCenter.default.post(name: .authStateDidChange, object: nil)

            return true
        } catch {
            let presentation = AuthErrorMapper.presentation(for: error)
            errorMessage = presentation.message
            showCreateAccountPrompt = false
            return false
        }
    }

    // MARK: - Sign in with Google
    
    /// Handles Sign in with Google
    /// - Parameter credential: The Google AuthCredential
    /// - Returns: True if successful, false otherwise
    @MainActor
    func handleGoogleSignIn(credential: AuthCredential) async -> Bool {
        // Clear previous errors
        errorMessage = nil
        showCreateAccountPrompt = false
        isLoading = true
        defer { isLoading = false }
        
        do {
            // Sign in with Google credential
            let (userID, isNewUser) = try await authService.signInWithGoogle(credential: credential)
            
            if isNewUser {
                // Get display name from Google account
                let displayName = authService.currentUserEmail ?? ""

                // Defer Firestore doc creation until the location gate passes
                // (RootView will show LocationGateView while pendingNewSSOUser is set).
                pendingNewSSOUser = PendingNewSSOUser(userID: userID, displayName: displayName, provider: "google")
                logOnboarding(path: "google", step: "locationGate", gate: .notRun)
            }

            // Update local state
            currentUserID = userID
            authState = .authenticated

            // Notify that auth state changed
            NotificationCenter.default.post(name: .authStateDidChange, object: nil)

            return true
        } catch {
            let presentation = AuthErrorMapper.presentation(for: error)
            errorMessage = presentation.message
            showCreateAccountPrompt = false
            return false
        }
    }

    // MARK: - Sign Out
    
    /// Signs out the current user
    /// - Returns: True if successful, false otherwise
    @MainActor
    func signOut() -> Bool {
        // Clear previous errors
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }
        
        do {
            try authService.signOut()

            // End any Google session (Google login or Google Calendar connection) so it
            // doesn't survive app sign-out. Safe no-op if Google was never used.
            GoogleSignInHelper().signOut()

            // Clear local state
            currentUserID = nil
            authState = .unauthenticated
            
            // Notify that auth state changed
            NotificationCenter.default.post(name: .authStateDidChange, object: nil)
            
            return true
        } catch {
            errorMessage = "Failed to sign out. Please try again."
            return false
        }
    }
    
    // MARK: - Password Reset
    
    /// Sends a password reset email to the specified address
    /// - Parameter email: User's email address
    /// - Returns: True if successful, false otherwise
    @MainActor
    func sendPasswordReset(email: String) async -> Bool {
        // Clear previous errors
        errorMessage = nil
        isLoading = true
        defer { isLoading = false }
        
        // Validate email
        guard validateEmail(email) else {
            errorMessage = "Please enter a valid email address."
            return false
        }
        
        do {
            try await authService.sendPasswordReset(email: email)
            return true
        } catch {
            errorMessage = "Failed to send password reset email. Please try again."
            return false
        }
    }
    
    // MARK: - SSO Location Gate

    /// Creates the Firestore user doc for a new SSO user after the location gate passes.
    @MainActor
    func createNewSSOUser(coordinate: CLLocationCoordinate2D) async -> Bool {
        guard let pending = pendingNewSSOUser else { return false }
        logOnboarding(path: pending.provider, step: "createAccount", gate: .passed)
        do {
            let user = FirebaseUser(
                id: pending.userID,
                displayName: pending.displayName,
                photoURLs: [],
                activities: [],
                daySlotCombos: [],
                latitude: coordinate.latitude,
                longitude: coordinate.longitude,
                radiusMiles: 10.0,
                isProfileComplete: false
            )
            try await firestoreService.createUser(user)
            pendingNewSSOUser = nil
            return true
        } catch {
            errorMessage = "Failed to create account. Please try again."
            return false
        }
    }

    /// Cancels a pending new SSO signup: deletes the Firebase Auth account and resets state.
    @MainActor
    func cancelNewSSOSignup() async {
        logOnboarding(path: pendingNewSSOUser?.provider ?? "sso", step: "waitlistOrCancel", gate: .failed)
        do {
            try await authService.deleteAccount()
        } catch {
            print("❌ AuthViewModel.cancelNewSSOSignup: \(error)")
        }
        pendingNewSSOUser = nil
        currentUserID = nil
        authState = .unauthenticated
        NotificationCenter.default.post(name: .authStateDidChange, object: nil)
    }

    // MARK: - Validation
    
    /// Validates sign up inputs
    private func validateSignUp(email: String, password: String, confirmPassword: String) -> Bool {
        // Check email
        guard validateEmail(email) else {
            errorMessage = "That doesn't look like a valid email address."
            return false
        }
        
        // Check password length
        guard password.count >= 6 else {
            errorMessage = "Password must be at least 6 characters."
            return false
        }
        
        // Check password match
        guard password == confirmPassword else {
            errorMessage = "Passwords do not match."
            return false
        }
        
        return true
    }
    
    /// Validates sign in inputs
    private func validateSignIn(email: String, password: String) -> Bool {
        // Check email
        guard validateEmail(email) else {
            errorMessage = "That doesn't look like a valid email address."
            return false
        }
        
        // Check password not empty
        guard !password.isEmpty else {
            errorMessage = "Please enter your password."
            return false
        }
        
        return true
    }
    
    /// Validates an email address format
    private func validateEmail(_ email: String) -> Bool {
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let emailPredicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)
        return emailPredicate.evaluate(with: email)
    }
    
    // MARK: - Utility Methods
    
    /// Clears any error messages
    func clearError() {
        errorMessage = nil
        showCreateAccountPrompt = false
    }
    
    /// The display name of the currently authenticated Firebase Auth user.
    var currentUserDisplayName: String {
        Auth.auth().currentUser?.displayName ?? ""
    }

    /// Checks if a user profile is complete
    /// - Returns: True if profile is complete, false otherwise
    func isProfileComplete() async -> Bool {
        guard let userID = currentUserID else { return false }
        
        do {
            if let user = try await firestoreService.fetchUser(userID: userID) {
                return user.isProfileComplete
            }
            return false
        } catch {
            return false
        }
    }
}

// MARK: - Onboarding Debug Logging

/// Status of the Austin location gate at the moment a routing decision is logged.
enum OnboardingGateStatus: String {
    case notRun, passed, failed
}

/// Traces onboarding routing decisions so a bad path (e.g. profile setup reachable
/// before the location gate) shows up in the console instead of only in a screenshot.
func logOnboarding(path: String, step: String, gate: OnboardingGateStatus) {
    #if DEBUG
    print("[Onboarding] path=\(path) step=\(step) gate=\(gate.rawValue)")
    #endif
}

// MARK: - Supporting Types

/// Carries the userID and displayName of a newly authenticated SSO user while they
/// complete the location gate before their Firestore doc is created.
struct PendingNewSSOUser {
    let userID: String
    let displayName: String
    /// "apple" or "google" — used only for [Onboarding] debug logging.
    let provider: String
}

/// Represents the current authentication state
enum AuthState: Equatable {
    case authenticated
    case unauthenticated
    
    var isAuthenticated: Bool {
        self == .authenticated
    }
}
