//
//  AccountDeletionService.swift
//  Avenue3
//

import Foundation
import AuthenticationServices
import FirebaseAuth
import FirebaseFunctions

/// Service layer for permanent account deletion. The actual deletion runs server-side in the
/// `deleteMyAccount` callable Cloud Function (it removes data other users can see, which the
/// client's security rules rightly block). This service also handles the Sign in with Apple
/// token revocation Apple requires on account deletion.
final class AccountDeletionService {
    static let shared = AccountDeletionService()

    private let functions = Functions.functions()

    /// Kept alive for the duration of the Apple re-authentication sheet.
    private var appleSignInHelper: AppleSignInHelper?

    private init() {}

    /// Whether the signed-in account uses Sign in with Apple.
    var isSignedInWithApple: Bool {
        Auth.auth().currentUser?.providerData.contains { $0.providerID == "apple.com" } ?? false
    }

    /// Re-authenticates with Apple to get a fresh authorization code, then revokes the
    /// app's Apple sign-in tokens via Firebase. Throws `ASAuthorizationError.canceled`
    /// if the user dismisses the Apple sheet.
    @MainActor
    func revokeAppleSignIn() async throws {
        let authorizationCode = try await requestAppleAuthorizationCode()
        try await Auth.auth().revokeToken(withAuthorizationCode: authorizationCode)
    }

    /// Calls `deleteMyAccount`, which deletes the caller's own account and everything they
    /// created, then the Firebase Auth user. Returns the IDs of the plans the user was part
    /// of, so per-plan local state can be cleared.
    func deleteMyAccount() async throws -> [String] {
        let result = try await functions.httpsCallable("deleteMyAccount").call()
        return (result.data as? [String: Any])?["planIDs"] as? [String] ?? []
    }

    // MARK: - Private

    @MainActor
    private func requestAppleAuthorizationCode() async throws -> String {
        let helper = AppleSignInHelper()
        appleSignInHelper = helper
        defer { appleSignInHelper = nil }

        let authorization: ASAuthorization = try await withCheckedThrowingContinuation { continuation in
            helper.signIn(nonce: UUID().uuidString) { result in
                continuation.resume(with: result)
            }
        }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let codeData = credential.authorizationCode,
              let code = String(data: codeData, encoding: .utf8) else {
            throw AuthError.invalidCredentials
        }
        return code
    }
}
