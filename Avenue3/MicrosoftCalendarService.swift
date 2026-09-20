//
//  MicrosoftCalendarService.swift
//  Avenue3
//

import Foundation
import MSAL
import UIKit

/// Errors surfaced while authorizing or writing to Outlook/Microsoft Calendar.
enum MicrosoftCalendarError: LocalizedError {
    case clientCreationFailed(Error)
    case authorizationFailed(Error)
    case missingAccessToken
    case requestFailed(statusCode: Int, message: String?)
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .clientCreationFailed(let error):
            return "Couldn't set up Microsoft Sign-In: \(error.localizedDescription)"
        case .authorizationFailed(let error):
            return "Couldn't get permission to access Outlook Calendar: \(error.localizedDescription)"
        case .missingAccessToken:
            return "Microsoft Sign-In didn't return an access token."
        case .requestFailed(let statusCode, let message):
            return message ?? "Outlook Calendar returned an error (status \(statusCode))."
        case .invalidResponse:
            return "Received an unexpected response from Outlook Calendar."
        }
    }
}

/// Handles interactive MSAL authorization and event creation against the Microsoft Graph
/// Calendar API. ATX Friends has no "Sign in with Microsoft" login option, so unlike Google
/// there's never an existing signed-in session to extend — every connection is a fresh,
/// independent MSAL sign-in used ONLY to obtain a Graph access token. It must never touch
/// Firebase Auth or the ATX Friends session.
struct MicrosoftCalendarService {
    private static let clientID = "1e9fc460-3333-45bf-8fb1-719e61ef5485"
    private static let authorityURL = URL(string: "https://login.microsoftonline.com/common")!
    private static let calendarScope = "Calendars.ReadWrite"

    /// Starts an interactive MSAL sign-in requesting Calendar access, returning the resulting
    /// Graph access token.
    func authorize(presenting viewController: UIViewController) async throws -> String {
        let application: MSALPublicClientApplication
        do {
            let authority = try MSALAADAuthority(url: Self.authorityURL)
            let config = MSALPublicClientApplicationConfig(clientId: Self.clientID, redirectUri: nil, authority: authority)
            application = try MSALPublicClientApplication(configuration: config)
        } catch {
            throw MicrosoftCalendarError.clientCreationFailed(error)
        }

        let webviewParameters = MSALWebviewParameters(authPresentationViewController: viewController)
        let interactiveParameters = MSALInteractiveTokenParameters(
            scopes: [Self.calendarScope],
            webviewParameters: webviewParameters
        )

        return try await withCheckedThrowingContinuation { continuation in
            application.acquireToken(with: interactiveParameters) { result, error in
                if let error {
                    continuation.resume(throwing: MicrosoftCalendarError.authorizationFailed(error))
                    return
                }
                guard let accessToken = result?.accessToken else {
                    continuation.resume(throwing: MicrosoftCalendarError.missingAccessToken)
                    return
                }
                continuation.resume(returning: accessToken)
            }
        }
    }

    /// Creates an event on the user's Outlook calendar matching the given plan. Mirrors the
    /// title/start/end/location/notes mapping used for the Apple EKEvent and Google Calendar
    /// event so all three providers produce equivalent events. Graph's event shape differs
    /// from Google's — built to Graph's own schema rather than reusing Google's JSON body.
    func createEvent(for plan: Plan, otherUserDisplayName: String, accessToken: String) async throws {
        let start = plan.confirmedDate ?? Date()
        let end = start.addingTimeInterval(2 * 60 * 60)

        var body: [String: Any] = [
            "subject": plan.activity.name,
            "start": ["dateTime": Self.graphDateTime.string(from: start), "timeZone": "UTC"],
            "end": ["dateTime": Self.graphDateTime.string(from: end), "timeZone": "UTC"],
            "body": ["contentType": "text", "content": "Hanging out with \(otherUserDisplayName)"]
        ]
        if let location = plan.location, !location.isEmpty {
            body["location"] = ["displayName": location]
        }

        var request = URLRequest(url: URL(string: "https://graph.microsoft.com/v1.0/me/events")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw MicrosoftCalendarError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let message = (json?["error"] as? [String: Any])?["message"] as? String
            throw MicrosoftCalendarError.requestFailed(statusCode: httpResponse.statusCode, message: message)
        }
    }

    /// Graph expects "yyyy-MM-ddTHH:mm:ss" with no UTC offset — the accompanying "timeZone"
    /// field ("UTC" here) is what disambiguates it, per Microsoft Graph's dateTimeTimeZone docs.
    private static let graphDateTime: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()
}
