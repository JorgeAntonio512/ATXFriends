//
//  LocationGateView.swift
//  Avenue3
//
//  Shown on the Register path before SignUpView. Requests location once,
//  checks distance to Austin (≤ 50 miles = proceed, > 50 miles = waitlist).
//  Permission denied/restricted routes to LocationPermissionNeededView instead —
//  that's a blocker they can clear, not a "you're not eligible" rejection.
//  Existing users never see this screen.
//

import SwiftUI
import CoreLocation

private let austinGateMaxMiles: Double = 50.0

struct LocationGateView: View {
    /// "email", "apple", or "google" — identifies which sign-up path reached this
    /// gate, for [Onboarding] debug logging only.
    let path: String
    /// Called by "Back" and WaitlistView's "Not now" to cancel the current
    /// sign-up path (email: pop to OnboardingView; SSO: delete pending account).
    let onDismissAll: () -> Void
    /// For SSO new-user flow: called with the verified coordinate on gate pass.
    /// When nil (default), the gate navigates internally to SignUpView (email path).
    let onGatePass: ((CLLocationCoordinate2D) -> Void)?

    init(
        path: String,
        onDismissAll: @escaping () -> Void,
        onGatePass: ((CLLocationCoordinate2D) -> Void)? = nil
    ) {
        self.path = path
        self.onDismissAll = onDismissAll
        self.onGatePass = onGatePass
    }

    @StateObject private var locationManager = LocationPermissionManager()
    @State private var showSignUp = false
    @State private var showWaitlist = false
    @State private var showPermissionNeeded = false
    @State private var capturedCoordinate: CLLocationCoordinate2D?
    @State private var isCheckingLocation = false
    /// Set when a location fetch fails or times out while checking. Non-nil shows an
    /// inline retry message; tapping "Continue" again clears it and starts a fresh attempt.
    @State private var locationErrorMessage: String?
    @Environment(\.scenePhase) private var scenePhase

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

                            Image(systemName: "location.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.white)
                        }

                        VStack(spacing: 12) {
                            Text("One quick check")
                                .font(.system(size: 30, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                                .multilineTextAlignment(.center)

                            Text("ATX Friends is Austin-only for now — we need to confirm you're nearby before creating your account.")
                                .font(.system(size: 17, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                        }
                        .padding(.horizontal, 40)

                        VStack(spacing: 8) {
                            HStack(spacing: 6) {
                                Image(systemName: "lock.shield.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(Color.appPrimary)

                                Text("Your Privacy")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            }

                            Text("We only check your location once, at signup. We don't track you after that.")
                                .font(.system(size: 13, weight: .regular, design: .rounded))
                                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))
                                .multilineTextAlignment(.center)
                                .lineSpacing(3)
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity)
                        .background(Color.appPrimary.opacity(0.1))
                        .cornerRadius(12)
                        .padding(.horizontal, 32)

                        Spacer().frame(height: 20)
                    }
                }
                .scrollIndicators(.hidden)
                .scrollBounceBehavior(.basedOnSize)

                VStack(spacing: 12) {
                    if let locationErrorMessage {
                        Text(locationErrorMessage)
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }

                    Button {
                        Task { await requestAndCheck() }
                    } label: {
                        HStack(spacing: 8) {
                            if isCheckingLocation {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 16))
                                Text("Continue")
                                    .font(.system(size: 18, weight: .semibold, design: .rounded))
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                colors: [Color.appNavy, Color.appNavy],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(color: Color.appNavy.opacity(0.3), radius: 12, x: 0, y: 6)
                    }
                    .disabled(isCheckingLocation)

                    Button {
                        onDismissAll()
                    } label: {
                        Text("Back")
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
        .navigationDestination(isPresented: $showSignUp) {
            SignUpView(coordinate: capturedCoordinate ?? .austin)
        }
        .navigationDestination(isPresented: $showWaitlist) {
            WaitlistView(onDismissAll: onDismissAll)
        }
        .navigationDestination(isPresented: $showPermissionNeeded) {
            LocationPermissionNeededView(
                locationManager: locationManager,
                onNotNow: onDismissAll,
                onAuthorizationResumed: { coord in
                    print("📍 [LocationGateView] onAuthorizationResumed callback received coord=\(String(describing: coord))")
                    showPermissionNeeded = false
                    if let coord {
                        evaluateLocation(coord)
                    } else {
                        // locationManagerDidChangeAuthorization already started the fetch (and
                        // its timeout) the moment authorization flipped — just reflect that as
                        // "checking" here rather than starting a second, redundant fetch.
                        locationErrorMessage = nil
                        isCheckingLocation = true
                    }
                }
            )
        }
        // NOTE: this handler only reliably fires while LocationGateView itself is the
        // topmost/visible view (e.g. the initial system permission prompt closing with
        // denied/restricted, which happens as an overlay on top of this screen, not a push).
        // On-device logging showed that once LocationPermissionNeededView is pushed on top
        // via navigationDestination, THIS view's onChange(of: locationManager.*) handlers stop
        // firing — so the "resume when granted" logic lives on LocationPermissionNeededView
        // itself (via onAuthorizationResumed above), not here.
        .onChange(of: locationManager.authorizationStatus) { oldStatus, newStatus in
            print("📍 [LocationGateView] authorizationStatus onChange: \(oldStatus.debugLabel) -> \(newStatus.debugLabel) | isCheckingLocation=\(isCheckingLocation) showPermissionNeeded=\(showPermissionNeeded)")
            guard isCheckingLocation else { return }
            switch newStatus {
            case .denied, .restricted:
                isCheckingLocation = false
                showPermissionNeeded = true
            default:
                break
            }
        }
        .onChange(of: locationManager.currentLocation) { _, newLocation in
            guard let coord = newLocation, isCheckingLocation else { return }
            print("📍 [LocationGateView] currentLocation onChange fired while isCheckingLocation — evaluating distance")
            evaluateLocation(coord)
        }
        // Catches a fetch that fails outright (terminal CLError) or times out — both are
        // published as lastErrorMessage by LocationPermissionManager. Without this, the
        // "Continue" button spins forever any time CoreLocation never delivers a fix, which
        // is not limited to the deny→Settings→return path this screen broke on previously.
        .onChange(of: locationManager.lastErrorMessage) { _, message in
            guard let message, isCheckingLocation else { return }
            print("📍 [LocationGateView] location fetch failed/timed out while isCheckingLocation — showing retry state")
            isCheckingLocation = false
            locationErrorMessage = message
        }
        // Secondary nudge, not the primary resume path: forces a fresh read of authorization
        // status on foreground in case the delegate callback is ever slow. Harmless — it just
        // writes to locationManager's @Published properties, which LocationPermissionNeededView
        // (the view actually live on screen at that point) observes directly and reliably.
        .onChange(of: scenePhase) { oldPhase, newPhase in
            print("📍 [LocationGateView] scenePhase onChange: \(oldPhase) -> \(newPhase) | showPermissionNeeded=\(showPermissionNeeded)")
            guard newPhase == .active, showPermissionNeeded else { return }
            print("📍 [LocationGateView] foregrounded while blocked — calling refreshAuthorizationStatus()")
            locationManager.refreshAuthorizationStatus()
        }
    }

    private func requestAndCheck() async {
        let currentStatus = locationManager.authorizationStatus
        guard currentStatus != .denied && currentStatus != .restricted else {
            showPermissionNeeded = true
            return
        }
        locationErrorMessage = nil
        isCheckingLocation = true
        await locationManager.requestOrStart()
        // If permission was already granted from an earlier signup attempt this app
        // session, LocationPermissionManager auto-starts fetching the moment it's created
        // (it reports its already-determined status immediately, not just on future
        // changes) — so a coordinate can already be sitting in currentLocation before we
        // ever get here. SwiftUI's onChange(of:) only fires on an actual value transition,
        // so if we only waited for that, an already-resolved coordinate would never be
        // consumed and this would spin forever. Check directly instead of only reacting.
        if isCheckingLocation, let coord = locationManager.currentLocation {
            print("📍 [LocationGateView] coordinate already available immediately after requestOrStart — evaluating directly")
            evaluateLocation(coord)
        }
    }

    private func evaluateLocation(_ coord: CLLocationCoordinate2D) {
        let userCL = CLLocation(latitude: coord.latitude, longitude: coord.longitude)
        let austinCL = CLLocation(latitude: CLLocationCoordinate2D.austin.latitude,
                                   longitude: CLLocationCoordinate2D.austin.longitude)
        let distanceMiles = userCL.distance(from: austinCL) / 1609.34

        isCheckingLocation = false
        locationErrorMessage = nil

        if distanceMiles <= austinGateMaxMiles {
            logOnboarding(path: path, step: "locationGate", gate: .passed)
            if let handler = onGatePass {
                // SSO path: hand coordinate to caller; caller creates the Firestore doc
                handler(coord)
            } else {
                // Email path: navigate to SignUpView internally
                capturedCoordinate = coord
                showSignUp = true
            }
        } else {
            logOnboarding(path: path, step: "waitlist", gate: .failed)
            showWaitlist = true
        }
    }
}

#Preview {
    NavigationStack {
        LocationGateView(path: "email", onDismissAll: {})
    }
}
