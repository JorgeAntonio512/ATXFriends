//
//  FirebaseAuthService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseAuth
import AuthenticationServices
import CryptoKit

/// Service layer for Firebase Authentication
/// Handles sign up, sign in, sign out, and user state monitoring
@Observable
final class FirebaseAuthService {
    // MARK: - Properties
    
    /// The currently authenticated user's ID (Firebase UID)
    private(set) var currentUserID: String?
    
    /// Whether a user is currently signed in
    var isAuthenticated: Bool {
        currentUserID != nil
    }
    
    /// Auth state listener handle
    private var authStateHandle: AuthStateDidChangeListenerHandle?
    
    /// Unhashed nonce for Sign in with Apple
    private var currentNonce: String?
    
    // MARK: - Singleton
    
    static let shared = FirebaseAuthService()
    
    private init() {
        // Start listening to auth state changes
        setupAuthStateListener()
    }
    
    deinit {
        // Remove auth state listener
        if let handle = authStateHandle {
            Auth.auth().removeStateDidChangeListener(handle)
        }
    }
    
    // MARK: - Auth State Monitoring
    
    /// Sets up listener for authentication state changes
    private func setupAuthStateListener() {
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            self?.currentUserID = user?.uid
        }
    }
    
    // MARK: - Sign Up
    
    /// Creates a new user account with email and password
    /// - Parameters:
    ///   - email: User's email address
    ///   - password: User's password
    /// - Returns: The Firebase UID of the newly created user
    /// - Throws: Authentication errors
    func signUp(email: String, password: String) async throws -> String {
        let authResult = try await Auth.auth().createUser(withEmail: email, password: password)
        return authResult.user.uid
    }
    
    // MARK: - Sign In
    
    /// Signs in an existing user with email and password
    /// - Parameters:
    ///   - email: User's email address
    ///   - password: User's password
    /// - Returns: The Firebase UID of the signed-in user
    /// - Throws: Authentication errors
    func signIn(email: String, password: String) async throws -> String {
        let authResult = try await Auth.auth().signIn(withEmail: email, password: password)
        return authResult.user.uid
    }
    
    // MARK: - Sign in with Apple
    
    /// Prepares for Sign in with Apple by generating a nonce
    /// - Returns: The hashed nonce to use in the Apple sign-in request
    func prepareAppleSignIn() -> String {
        let nonce = randomNonceString()
        currentNonce = nonce
        return sha256(nonce)
    }
    
    /// Signs in with Apple credential
    /// - Parameters:
    ///   - credential: The Apple ID credential from the authorization
    /// - Returns: Tuple containing the Firebase UID and whether this is a new user
    /// - Throws: Authentication errors
    func signInWithApple(credential: ASAuthorizationAppleIDCredential) async throws -> (userID: String, isNewUser: Bool) {
        guard let nonce = currentNonce else {
            throw AuthError.invalidCredentials
        }
        
        guard let appleIDToken = credential.identityToken,
              let idTokenString = String(data: appleIDToken, encoding: .utf8) else {
            throw AuthError.invalidCredentials
        }
        
        // Create Firebase credential
        let firebaseCredential = OAuthProvider.credential(
            providerID: .apple,
            idToken: idTokenString,
            rawNonce: nonce
        )
        
        // Sign in with Firebase
        let authResult = try await Auth.auth().signIn(with: firebaseCredential)
        
        // Check if this is a new user
        let isNewUser = authResult.additionalUserInfo?.isNewUser ?? false
        
        // Clear the nonce
        currentNonce = nil
        
        return (authResult.user.uid, isNewUser)
    }
    
    // MARK: - Sign in with Google
    
    /// Signs in with Google credential
    /// - Parameter credential: The Google AuthCredential
    /// - Returns: Tuple containing the Firebase UID and whether this is a new user
    /// - Throws: Authentication errors
    func signInWithGoogle(credential: AuthCredential) async throws -> (userID: String, isNewUser: Bool) {
        // Sign in with Firebase
        let authResult = try await Auth.auth().signIn(with: credential)
        
        // Check if this is a new user
        let isNewUser = authResult.additionalUserInfo?.isNewUser ?? false
        
        return (authResult.user.uid, isNewUser)
    }
    
    // MARK: - Sign Out
    
    /// Signs out the current user
    /// - Throws: Sign out errors
    func signOut() throws {
        try Auth.auth().signOut()
    }
    
    // MARK: - Password Reset
    
    /// Sends a password reset email to the specified email address
    /// - Parameter email: The user's email address
    /// - Throws: Password reset errors
    func sendPasswordReset(email: String) async throws {
        try await Auth.auth().sendPasswordReset(withEmail: email)
    }
    
    // MARK: - Delete Account
    
    /// Deletes the currently authenticated user's account
    /// - Throws: Account deletion errors
    func deleteAccount() async throws {
        guard let user = Auth.auth().currentUser else {
            throw AuthError.noUserSignedIn
        }
        try await user.delete()
    }
    
    // MARK: - Helpers
    
    /// Returns the current user's email if signed in
    var currentUserEmail: String? {
        Auth.auth().currentUser?.email
    }
    
    // MARK: - Apple Sign In Helpers
    
    /// Generates a random nonce for Sign in with Apple
    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remainingLength = length
        
        while remainingLength > 0 {
            let randoms: [UInt8] = (0..<16).map { _ in
                var random: UInt8 = 0
                let errorCode = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                if errorCode != errSecSuccess {
                    fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
                }
                return random
            }
            
            randoms.forEach { random in
                if remainingLength == 0 {
                    return
                }
                
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remainingLength -= 1
                }
            }
        }
        
        return result
    }
    
    /// Hashes a string using SHA256
    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        let hashString = hashedData.compactMap {
            String(format: "%02x", $0)
        }.joined()
        
        return hashString
    }
}

// MARK: - Custom Errors

enum AuthError: LocalizedError {
    case noUserSignedIn
    case invalidCredentials
    case networkError
    case unknown
    
    var errorDescription: String? {
        switch self {
        case .noUserSignedIn:
            return "No user is currently signed in."
        case .invalidCredentials:
            return "Invalid email or password."
        case .networkError:
            return "Network connection error. Please try again."
        case .unknown:
            return "An unknown error occurred."
        }
    }
}
