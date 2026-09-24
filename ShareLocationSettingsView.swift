//
//  ShareLocationSettingsView.swift
//  Avenue3
//
//  The "Share My Location" section embedded inline in the Settings tab.
//

import SwiftUI
import CoreLocation

/// Inline content for the "Share My Location" settings card: the mode picker,
/// explainer, last-updated label, "Update now" button, and the permission-denied
/// message with a deep link to iOS Settings.
struct ShareMyLocationContent: View {
    @Bindable var viewModel: ProfileViewModel
    @ObservedObject private var locationManager = LocationSharingManager.shared

    @State private var selectedMode: LocationSharingMode = .off
    @State private var isSyncingFromViewModel = false
    @State private var isUpdating = false
    @State private var showPermissionDeniedAlert = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("Share My Location", selection: $selectedMode) {
                ForEach(LocationSharingMode.allCases, id: \.self) { mode in
                    Text(mode.displayName).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .disabled(isUpdating)

            Text("Your matches see about how far away you are — never your exact location.")
                .font(.system(size: 13, weight: .regular, design: .rounded))
                .foregroundColor(Color(red: 0.55, green: 0.55, blue: 0.55))

            if let locationUpdatedAt = viewModel.locationUpdatedAt {
                Text("Last updated: \(lastUpdatedLabel(locationUpdatedAt))")
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
            }

            if selectedMode == .once {
                Button {
                    Task { await updateNow() }
                } label: {
                    HStack(spacing: 8) {
                        if isUpdating {
                            ProgressView().tint(Color.appPrimary)
                        } else {
                            Image(systemName: "location.fill")
                                .font(.system(size: 14))
                            Text("Update now")
                                .font(.system(size: 15, weight: .semibold, design: .rounded))
                        }
                    }
                    .foregroundColor(Color.appPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color.appPrimary.opacity(0.12))
                    .cornerRadius(12)
                }
                .disabled(isUpdating)
            }
        }
        .onAppear { syncSelectedMode() }
        .onChange(of: viewModel.locationSharingMode) { _, _ in syncSelectedMode() }
        .onChange(of: selectedMode) { oldValue, newValue in
            guard !isSyncingFromViewModel, oldValue != newValue else { return }
            print("[LOCSHARE] mode change requested: \(oldValue.rawValue) -> \(newValue.rawValue)")
            Task { await handleModeChange(newValue) }
        }
        .alert("Location access needed", isPresented: $showPermissionDeniedAlert) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("ATX Friends can't access your location. Enable it in Settings to share your distance with matches.")
        }
    }

    private func syncSelectedMode() {
        isSyncingFromViewModel = true
        selectedMode = viewModel.locationSharingMode
        isSyncingFromViewModel = false
    }

    private func lastUpdatedLabel(_ date: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(date) { return "today" }
        if let days = calendar.dateComponents([.day], from: date, to: Date()).day, days < 7 {
            return "this week"
        }
        return "over a week ago"
    }

    private func handleModeChange(_ newMode: LocationSharingMode) async {
        guard newMode != .off else {
            _ = await viewModel.updateLocationSharing(mode: .off)
            return
        }

        isUpdating = true
        defer { isUpdating = false }

        locationManager.refreshAuthorizationStatus()
        print("[LOCSHARE] permission status: \(locationManager.authorizationStatus.debugLabel)")

        if locationManager.isDeniedOrRestricted {
            revertToOffWithPermissionMessage()
            return
        }
        if locationManager.authorizationStatus == .notDetermined {
            await locationManager.requestPermissionIfNeeded()
        }
        guard locationManager.isAuthorized else {
            revertToOffWithPermissionMessage()
            return
        }

        guard let coordinate = await locationManager.fetchCoarseLocation() else {
            // Fetch failed/timed out — keep the old stored location, no scary error.
            // The mode preference itself is still valid, so it's saved as chosen.
            _ = await viewModel.updateLocationSharing(mode: newMode)
            return
        }
        _ = await viewModel.updateLocationSharing(mode: newMode, coordinate: coordinate)
    }

    private func updateNow() async {
        isUpdating = true
        defer { isUpdating = false }

        locationManager.refreshAuthorizationStatus()
        guard locationManager.isAuthorized else {
            revertToOffWithPermissionMessage()
            return
        }
        guard let coordinate = await locationManager.fetchCoarseLocation() else {
            return
        }
        _ = await viewModel.updateLocationSharing(mode: .once, coordinate: coordinate)
    }

    private func revertToOffWithPermissionMessage() {
        print("[LOCSHARE] permission denied/restricted — reverting mode to off")
        selectedMode = .off
        showPermissionDeniedAlert = true
        Task { _ = await viewModel.updateLocationSharing(mode: .off) }
    }
}

#Preview {
    ShareMyLocationContent(viewModel: ProfileViewModel())
        .padding()
}
