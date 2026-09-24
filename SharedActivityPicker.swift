//
//  SharedActivityPicker.swift
//  Avenue3
//

import SwiftUI

// Parked for post-alpha (2026-09-19): extracted from CreatePlanView.swift before that file was
// deleted. Nothing routes to this view — ProposePlanSheet uses free-text activity entry with
// suggestion chips instead — but "show activities shared with this match first" is a genuinely
// good idea worth revisiting once alpha settles.

/// Activity picker that shows activities shared with a specific match first (starred),
/// then all other activities behind a search field.
struct SharedActivityPicker: View {
    let sharedActivities: [Activity]
    let allActivities: [Activity]
    @Binding var selectedActivity: Activity?

    @State private var activitySearchText: String = ""
    @FocusState private var isActivitySearchFocused: Bool

    private var filteredOtherActivities: [Activity] {
        let otherActivities = allActivities.filter { activity in
            !sharedActivities.contains(where: { $0.id == activity.id })
        }
        if activitySearchText.isEmpty { return otherActivities }
        return otherActivities.filter {
            $0.name.localizedCaseInsensitiveContains(activitySearchText)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "figure.run")
                    .font(.system(size: 16))
                    .foregroundColor(Color.appPrimary)
                Text("What activity?")
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
            }

            if !sharedActivities.isEmpty {
                Text("Shared Activities")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                    .padding(.leading, 4)

                ForEach(sharedActivities, id: \.id) { activity in
                    ActivitySelectionRow(
                        activity: activity,
                        isSelected: selectedActivity?.id == activity.id,
                        isShared: true,
                        onTap: { withAnimation { selectedActivity = activity } }
                    )
                }
            }

            if !allActivities.isEmpty {
                Text("Other Activities")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimary)
                    .padding(.leading, 4)
                    .padding(.top, sharedActivities.isEmpty ? 0 : 8)

                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color.appPrimary)
                    TextField("Search activities...", text: $activitySearchText)
                        .font(.system(size: 15, weight: .regular, design: .rounded))
                        .focused($isActivitySearchFocused)
                        .submitLabel(.done)
                    if !activitySearchText.isEmpty {
                        Button {
                            activitySearchText = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Color.appSecondaryText)
                        }
                    }
                }
                .padding()
                .background(Color.appCardBackground.opacity(0.7))
                .cornerRadius(12)

                if !activitySearchText.isEmpty {
                    if filteredOtherActivities.isEmpty {
                        Text("No activities found")
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appTextMuted)
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(Color.appCardBackground.opacity(0.5))
                            .cornerRadius(12)
                    } else {
                        ForEach(filteredOtherActivities, id: \.id) { activity in
                            ActivitySelectionRow(
                                activity: activity,
                                isSelected: selectedActivity?.id == activity.id,
                                isShared: false,
                                onTap: {
                                    withAnimation {
                                        selectedActivity = activity
                                        activitySearchText = ""
                                        isActivitySearchFocused = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/// Single row in SharedActivityPicker — a name, a shared-activity star, and a selection checkmark.
struct ActivitySelectionRow: View {
    let activity: Activity
    let isSelected: Bool
    let isShared: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Image(systemName: "figure.run")
                    .foregroundColor(isShared ? Color.appPrimary : Color.appSecondaryText)

                Text(activity.name)
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)

                if isShared {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundColor(Color.appPrimary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(Color.appPrimary)
                }
            }
            .padding()
            .background(
                isSelected ?
                Color.appPrimary.opacity(0.15) :
                Color.appCardBackground.opacity(0.6)
            )
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(
                        isSelected ?
                        Color.appPrimary :
                        Color.clear,
                        lineWidth: 2
                    )
            )
        }
        .buttonStyle(.plain)
    }
}
