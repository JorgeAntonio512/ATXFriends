//
//  AuthErrorMessage.swift
//  Avenue3
//
//  Single source of truth for turning a Firebase Auth error into a
//  user-facing message. Sign-in and sign-up both route through this so the
//  mapping only lives in one place.
//

import Foundation
import FirebaseAuth

/// The result of mapping a Firebase Auth error to something shown on screen.
struct AuthErrorPresentation {
    let message: String
    /// True when the error is the combined "wrong password or unknown email"
    /// case, where the sign-in screen should also offer a way to create an account.
    let suggestsAccountCreation: Bool
}

enum AuthErrorMapper {
    /// Maps a Firebase Auth error to a user-facing message.
    ///
    /// Sign-in failures for a wrong password and for an unknown email must show
    /// the same message — telling the user "no account with that email" would let
    /// anyone probe which emails are registered, which is exactly what Firebase's
    /// combined `invalidCredential` error is designed to prevent.
    static func presentation(for error: Error) -> AuthErrorPresentation {
        let nsError = error as NSError
        #if DEBUG
        print("[AuthError] code=\(nsError.code) domain=\(nsError.domain)")
        #endif

        switch AuthErrorCode(rawValue: nsError.code) {
        case .invalidCredential, .wrongPassword, .userNotFound:
            return AuthErrorPresentation(
                message: "Email or password is incorrect.",
                suggestsAccountCreation: true
            )
        case .invalidEmail:
            return AuthErrorPresentation(
                message: "That doesn't look like a valid email address.",
                suggestsAccountCreation: false
            )
        case .networkError:
            return AuthErrorPresentation(
                message: "Can't connect right now. Check your internet and try again.",
                suggestsAccountCreation: false
            )
        case .tooManyRequests:
            return AuthErrorPresentation(
                message: "Too many attempts. Wait a few minutes and try again.",
                suggestsAccountCreation: false
            )
        case .userDisabled:
            return AuthErrorPresentation(
                message: "This account has been disabled.",
                suggestsAccountCreation: false
            )
        case .emailAlreadyInUse:
            return AuthErrorPresentation(
                message: "An account with this email already exists. Try signing in instead.",
                suggestsAccountCreation: false
            )
        case .weakPassword:
            return AuthErrorPresentation(
                message: "That password is too weak. Try a longer one.",
                suggestsAccountCreation: false
            )
        default:
            return AuthErrorPresentation(
                message: "Something went wrong. Please try again.",
                suggestsAccountCreation: false
            )
        }
    }
}
