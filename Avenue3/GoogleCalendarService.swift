//
//  GoogleCalendarService.swift
//  Avenue3
//

import Foundation
import GoogleSignIn
import FirebaseCore
import UIKit

/// Errors surfaced while authorizing or writing to Google Calendar.
enum GoogleCalendarError: LocalizedError {
    case missingGoogleClientID
    case authorizationFailed(Error)
    case requestFailed(statusCode: Int, message: String?)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .missingGoogleClientID:
            return "Google Sign-In isn't configured on this device. Try again later."
        case .authorizationFailed(let error):
            return "Couldn't get permission to access Google Calendar: \(error.localizedDescription)"
        case .requestFailed(let statusCode, let message):
            return message ?? "Google Calendar returned an error (status \(statusCode))."
        case .invalidResponse:
            return "Received an unexpected response from Google Calendar."
        }
    }
}

/// Handles incremental OAuth authorization for the Calendar scope and event creation
/// against the Google Calendar REST API. Uses the already-signed-in `GIDSignIn` user —
/// no additional Info.plist entries or URL schemes are required beyond what Google
/// Sign-In already registers.
struct GoogleCalendarService {
    static let calendarEventsScope = "https://www.googleapis.com/auth/calendar.events"

    /// Ensures we hold a Google access token with the calendar.events scope granted,
    /// prompting for consent if needed, then returns that token.
    ///
    /// Two entirely separate paths, depending on whether the person has ever connected
    /// a Google account to this device's `GIDSignIn` session:
    /// - If `GIDSignIn.sharedInstance.currentUser` is already set (they either logged into
    ///   ATX Friends with Google, or connected a calendar before), we just add the scope to
    ///   that existing Google session via `addScopes`.
    /// - If it's nil — e.g. they logged into ATX Friends via email or Apple and have never
    ///   touched Google Sign-In — `addScopes` has no user to add a scope to. We fall back to
    ///   a fresh interactive Google sign-in requesting the calendar scope up front. This is
    ///   a brand-new, independent Google OAuth session used ONLY to obtain a Calendar access
    ///   token: it must never call `Auth.auth().signIn`, build a `GoogleAuthProvider`
    ///   credential, or otherwise touch Firebase Auth / the ATX Friends session — doing so
    ///   would risk switching or re-authenticating the user's actual app account. The chosen
    ///   Google account does not need to (and normally won't) match however they log into
    ///   ATX Friends.
    func authorize(presenting viewController: UIViewController) async throws -> String {
        // Both branches below funnel into GIDSignIn's internal `signInWithOptions:`
        // (addScopes included — it's not a separate, lighter-weight call), which throws
        // an uncaught NSInvalidArgumentException if `GIDSignIn.sharedInstance.configuration`
        // itself is nil, regardless of whether `currentUser.configuration` is already set.
        // That property is never restored from Keychain and is only ever set by app code,
        // so it must be set here explicitly, before either branch — not just in the
        // sign-in-from-scratch branch, which is where the previous fix mistakenly assumed
        // the crash was confined to. Read the OAuth client ID the same way
        // GoogleSignInHelper.signIn() does: from Firebase's already-loaded config. This is
        // a config read only — no sign-in, no Firebase Auth session interaction.
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            throw GoogleCalendarError.missingGoogleClientID
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let configuration = GIDSignIn.sharedInstance.configuration else {
            throw GoogleCalendarError.missingGoogleClientID
        }
        // TODO(calendar-09 debug): remove once George confirms this path stops crashing.
        print("🔍 [GoogleCalendarService] configuration set — clientID: \(configuration.clientID)")

        if let currentUser = GIDSignIn.sharedInstance.currentUser {
            print("🔍 [GoogleCalendarService] currentUser present — taking addScopes path")
            return try await addCalendarScope(to: currentUser, presenting: viewController)
        }
        print("🔍 [GoogleCalendarService] no currentUser — taking sign-in-from-scratch path")
        return try await signInForCalendarAccess(presenting: viewController)
    }

    private func addCalendarScope(to currentUser: GIDGoogleUser, presenting viewController: UIViewController) async throws -> String {
        if currentUser.grantedScopes?.contains(Self.calendarEventsScope) == true {
            return currentUser.accessToken.tokenString
        }

        return try await withCheckedThrowingContinuation { continuation in
            print("🔍 [GoogleCalendarService] about to call currentUser.addScopes — GIDSignIn.sharedInstance.configuration = \(String(describing: GIDSignIn.sharedInstance.configuration?.clientID))")
            currentUser.addScopes(
                [Self.calendarEventsScope],
                presenting: viewController
            ) { signInResult, error in
                if let error {
                    let nsError = error as NSError
                    if nsError.domain == kGIDSignInErrorDomain,
                       nsError.code == GIDSignInError.Code.scopesAlreadyGranted.rawValue {
                        // Scope was already granted in a prior session — proceed as success.
                        continuation.resume(returning: currentUser.accessToken.tokenString)
                        return
                    }
                    continuation.resume(throwing: GoogleCalendarError.authorizationFailed(error))
                    return
                }
                let token = signInResult?.user.accessToken.tokenString ?? currentUser.accessToken.tokenString
                continuation.resume(returning: token)
            }
        }
    }

    /// `GIDSignIn.sharedInstance.configuration` is already guaranteed set by `authorize(presenting:)`
    /// before this is called.
    private func signInForCalendarAccess(presenting viewController: UIViewController) async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            print("🔍 [GoogleCalendarService] about to call GIDSignIn.signIn(withPresenting:) — configuration = \(String(describing: GIDSignIn.sharedInstance.configuration?.clientID))")
            GIDSignIn.sharedInstance.signIn(
                withPresenting: viewController,
                hint: nil,
                additionalScopes: [Self.calendarEventsScope]
            ) { signInResult, error in
                if let error {
                    continuation.resume(throwing: GoogleCalendarError.authorizationFailed(error))
                    return
                }
                guard let user = signInResult?.user else {
                    continuation.resume(throwing: GoogleCalendarError.invalidResponse)
                    return
                }
                continuation.resume(returning: user.accessToken.tokenString)
            }
        }
    }

    /// Creates an event on the user's primary Google Calendar matching the given plan.
    /// Mirrors the title/start/end/location/notes mapping used for the Apple EKEvent so
    /// both providers produce equivalent events.
    func createEvent(for plan: Plan, otherUserDisplayName: String, accessToken: String) async throws {
        let start = plan.confirmedDate ?? Date()
        let end = start.addingTimeInterval(2 * 60 * 60)

        var body: [String: Any] = [
            "summary": plan.activity.name,
            "start": ["dateTime": Self.iso8601.string(from: start)],
            "end": ["dateTime": Self.iso8601.string(from: end)],
            "description": "Hanging out with \(otherUserDisplayName)"
        ]
        if let location = plan.location, !location.isEmpty {
            body["location"] = location
        }

        var request = URLRequest(url: URL(string: "https://www.googleapis.com/calendar/v3/calendars/primary/events")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GoogleCalendarError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let message = (json?["error"] as? [String: Any])?["message"] as? String
            throw GoogleCalendarError.requestFailed(statusCode: httpResponse.statusCode, message: message)
        }
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}
