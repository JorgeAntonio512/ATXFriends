//
//  LocationPermissionNeededView.swift
//  Avenue3
//
//  Shown when the user denies (or is restricted from granting) the location
//  permission prompt in LocationGateView. Unlike WaitlistView, this is not a
//  rejection — the person may well be in Austin, we just can't confirm it yet.
//
//  IMPORTANT: this screen — not LocationGateView underneath it — must own the
//  onChange observers for locationManager's @Published properties. On-device
//  logging proved that once this view is pushed on top of LocationGateView via
//  navigationDestination, LocationGateView's own onChange(of:) handlers for
//  locationManager stop firing (SwiftUI skips Combine-driven view invalidation
//  for views that are no longer part of the visible render tree — scenePhase,
//  being an Environment value, is the exception and keeps propagating). So the
//  resume logic has to live here, on whichever view is actually on screen.
//

import SwiftUI
import CoreLocation

struct LocationPermissionNeededView: View {
    @ObservedObject var locationManager: LocationPermissionManager
    /// Same "back out" contract as WaitlistView's onDismissAll: for the SSO path,
    /// deletes the pending Firebase Auth account; for the email path, returns to OnboardingView.
    let onNotNow: () -> Void
    /// Called as soon as permission is granted while this screen is showing. Passes the
    /// coordinate directly if one already arrived (the common case — CLLocationManager
    /// starts updating the instant authorization flips), or nil if authorization was granted
    /// but no fix has come in yet, in which case the caller should kick off a fresh request.
    let onAuthorizationResumed: (CLLocationCoordinate2D?) -> Void

    @Environment(\.openURL) private var openURL
    @State private var hasResumed = false

    private var authorizationStatus: CLAuthorizationStatus { locationManager.authorizationStatus }
    private var isRestricted: Bool { authorizationStatus == .restricted }

    private var bodyText: String {
        if isRestricted {
            return "Your device doesn't allow changing this setting. If that's unexpected, check with whoever manages this device — for example parental controls or a work profile."
        } else {
            return "We need your location to confirm you're in the Austin area. Open Settings and turn on Location for ATX Friends, then come back — we'll pick up right where you left off."
        }
    }

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 28) {
                        Spacer().frame(height: 32)

                        ZStack {
                            Circle()
                                .fill(Color.appPrimary.opacity(0.15))
                                .frame(width: 160, height: 160)

                            Circle()
                                .fill(Color.appPrimary)
                                .frame(width: 110, height: 110)
                                .shadow(color: Color.appPrimary.opacity(0.3), radius: 20, x: 0, y: 10)

                            Image(systemName: "location.slash.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.white)
                        }

                        VStack(spacing: 12) {
                            Text("We need your location")
                                .font(.system(size: 28, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .multilineTextAlignment(.center)

                            Text(bodyText)
                                .font(.system(size: 17, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                        }
                        .padding(.horizontal, 40)

                        Spacer().frame(height: 20)
                    }
                }
                .scrollIndicators(.hidden)

                VStack(spacing: 12) {
                    if !isRestricted {
                        Button {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                openURL(url)
                            }
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "gear")
                                    .font(.system(size: 16))
                                Text("Open Settings")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color.appNavy)
                            .cornerRadius(16)
                            .shadow(color: Color.appNavy.opacity(0.3), radius: 12, x: 0, y: 6)
                        }
                    }

                    Button {
                        onNotNow()
                    } label: {
                        Text("Not now")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appPrimary)
                    }
                }
                .padding(.horizontal, 32)
                .padding(.vertical, 20)
                .background(
                    LinearGradient(
                        colors: [Color.white.opacity(0.95), Color.white.opacity(0.95)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
                )
            }
        }
        .navigationBarBackButtonHidden(true)
        // Live observers — this view is the one actually on screen while blocked, so unlike
        // LocationGateView's equivalents, these are guaranteed to fire. Either signal alone
        // is enough to resume; `hasResumed` stops both from double-firing if they land together.
        .onChange(of: locationManager.currentLocation) { _, newLocation in
            guard let coord = newLocation, !hasResumed else { return }
            print("📍 [LocationPermissionNeededView] currentLocation onChange fired while blocked — resuming with coordinate \(coord)")
            hasResumed = true
            onAuthorizationResumed(coord)
        }
        .onChange(of: locationManager.authorizationStatus) { _, newStatus in
            print("📍 [LocationPermissionNeededView] authorizationStatus onChange: \(newStatus.debugLabel) | hasResumed=\(hasResumed)")
            guard !hasResumed, newStatus == .authorizedWhenInUse || newStatus == .authorizedAlways else { return }
            guard locationManager.currentLocation == nil else { return } // the currentLocation branch already handles this case
            print("📍 [LocationPermissionNeededView] authorized with no location yet — resuming without a coordinate")
            hasResumed = true
            onAuthorizationResumed(nil)
        }
    }
}

#Preview("Denied") {
    NavigationStack {
        LocationPermissionNeededView(
            locationManager: LocationPermissionManager(),
            onNotNow: {},
            onAuthorizationResumed: { _ in }
        )
    }
}
