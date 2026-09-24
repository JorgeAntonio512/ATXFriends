//
//  PlanLocationField.swift
//  Avenue3
//

import SwiftUI
import CoreLocation

/// Shared "Where?" field for every plan-creation surface (ProposePlanSheet, CreateTodayPlanView).
/// Required — a plan with no location is useless. Free-typed text is valid; a MapKit match
/// is not required. Coordinates are optional and only feed the calendar-integration deep links.
struct PlanLocationField: View {
    @Binding var text: String
    @Binding var selectedLocationName: String?
    @Binding var selectedCoordinate: CLLocationCoordinate2D?
    let userCoordinate: CLLocationCoordinate2D?

    /// Optional scroll wiring so the field (and its results) scroll into view above the
    /// keyboard on focus. Pass both or neither.
    var scrollProxy: ScrollViewProxy? = nil
    var scrollAnchorID: AnyHashable? = nil

    @State private var locationSearch = LocationSearchViewModel(userCoordinate: nil)
    @FocusState private var isFocused: Bool
    /// True once the user has focused then left the field — gates the missing-location
    /// hint so a freshly opened, untouched sheet never shows it.
    @State private var hasBeenTouched = false

    private var isMissing: Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Red hint is only ever shown after the field has been touched and left empty —
    /// it clears the instant the text becomes non-empty, since `isMissing` flips false.
    private var showMissingHint: Bool {
        hasBeenTouched && isMissing
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Where?", systemImage: "mappin.circle.fill")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimary)

            HStack(spacing: 10) {
                TextField("Search for a place…", text: $text)
                    .font(.system(size: 16, design: .rounded))
                    .textFieldStyle(.plain)
                    .focused($isFocused)
                    .onChange(of: text) { _, newValue in
                        if newValue != selectedLocationName {
                            selectedLocationName = nil
                            selectedCoordinate = nil
                        }
                        locationSearch.queryFragment = newValue
                    }

                if !text.isEmpty {
                    Button {
                        text = ""
                        selectedLocationName = nil
                        selectedCoordinate = nil
                        locationSearch.queryFragment = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Color.appTextMuted)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.appCardBackground.opacity(0.85))
            .cornerRadius(12)
            .id(scrollAnchorID)
            .onChange(of: isFocused) { wasFocused, focused in
                if wasFocused && !focused {
                    hasBeenTouched = true
                }
                guard focused, let scrollProxy, let scrollAnchorID else { return }
                // Wait for the keyboard's own animation so the scroll lands after it settles.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    withAnimation { scrollProxy.scrollTo(scrollAnchorID, anchor: .bottom) }
                }
            }

            if isFocused {
                switch locationSearch.state {
                case .searching:
                    statusRow("Searching…", showsProgress: true)
                case .noMatches:
                    statusRow("No matches — you can enter any location", icon: "info.circle")
                case .error:
                    statusRow("Couldn't search right now — you can still type a location", icon: "exclamationmark.circle")
                case .hasResults:
                    resultsList
                case .idle:
                    EmptyView()
                }
            }

            if showMissingHint {
                Text("Add a place so people know where to meet")
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.red)
            }
        }
        .onAppear { locationSearch.setUserCoordinate(userCoordinate) }
        .onChange(of: userCoordinate?.latitude) { _, _ in
            locationSearch.setUserCoordinate(userCoordinate)
        }
    }

    private var resultsList: some View {
        VStack(spacing: 0) {
            ForEach(locationSearch.results) { result in
                Button {
                    selectLocation(result)
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "mappin.circle.fill")
                            .foregroundColor(Color.appPrimary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(result.name)
                                .font(.system(size: 15, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appPrimaryText)
                                .lineLimit(1)
                            if let address = result.address {
                                Text(address)
                                    .font(.system(size: 12, design: .rounded))
                                    .foregroundColor(Color.appTextTertiary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        if let distanceMiles = result.distanceMiles {
                            Text(distanceMiles < 1 ? String(format: "%.1f mi", distanceMiles) : String(format: "%.0f mi", distanceMiles))
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appTextTertiary)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)

                if result.id != locationSearch.results.last?.id {
                    Divider().padding(.leading, 14)
                }
            }
        }
        .background(Color.appCardBackground.opacity(0.9))
        .cornerRadius(12)
    }

    private func statusRow(_ label: String, icon: String? = nil, showsProgress: Bool = false) -> some View {
        HStack(spacing: 8) {
            if showsProgress {
                ProgressView().tint(Color.appPrimary).scaleEffect(0.7)
            } else if let icon {
                Image(systemName: icon)
                    .font(.system(size: 12))
                    .foregroundColor(Color.appTextTertiary)
            }
            Text(label)
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(Color.appTextTertiary)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
    }

    @MainActor
    private func selectLocation(_ result: LocationSearchViewModel.Result) {
        selectedLocationName = result.name
        selectedCoordinate = result.coordinate
        text = result.name
        locationSearch.queryFragment = ""
        isFocused = false
    }
}
