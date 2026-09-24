//
//  UserProfileView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

/// Full-screen user profile view showing complete 3-by-3 profile
struct UserProfileView: View {
    @Environment(\.dismiss) private var dismiss
    let user: FirebaseUser
    let currentUser: FirebaseUser?
    @State private var currentPhotoIndex: Int = 0
    @State private var fullScreenURL: String? = nil

    // MARK: - Computed Properties

    private var sharedActivities: [Activity] {
        guard let current = currentUser else { return [] }
        let currentActivityIDs = Set(current.activities.map { $0.id })
        return user.activities.filter { currentActivityIDs.contains($0.id) }
    }

    private var sharedTimes: [DaySlotCombo] {
        guard let current = currentUser else { return [] }
        let currentTimes = Set(current.daySlotCombos)
        return user.daySlotCombos.filter { currentTimes.contains($0) }
    }

    private var distance: String {
        guard let current = currentUser else { return "" }
        let userLocation = CLLocation(latitude: user.latitude, longitude: user.longitude)
        let currentLocation = CLLocation(latitude: current.latitude, longitude: current.longitude)
        let distanceInMeters = currentLocation.distance(from: userLocation)
        let distanceInMiles = distanceInMeters / 1609.34
        return String(format: "%.1f", distanceInMiles)
    }

    private var wouldMatch: Bool {
        !sharedActivities.isEmpty && !sharedTimes.isEmpty
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            ZStack {
                backgroundGradient
                ScrollView {
                    VStack(spacing: 24) {
                        photoSection
                        nameAndModeSection
                        if wouldMatch { matchIndicator }
                        activitiesSection
                        availabilitySection
                        matchInfoSection
                        Spacer().frame(height: 40)
                    }
                    .padding(.top, 20)
                }
                .scrollIndicators(.hidden)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 28))
                            .foregroundColor(Color.appTextMuted)
                            .symbolRenderingMode(.hierarchical)
                    }
                }
            }
            .fullScreenCover(item: Binding(
                get: { fullScreenURL.map { FullScreenPhotoURL(url: $0) } },
                set: { fullScreenURL = $0?.url }
            )) { item in
                fullScreenPhotoView(url: item.url)
            }
        }
    }

    // MARK: - Sub-views

    private var backgroundGradient: some View {
        LinearGradient(
            colors: [
                Color.appBackground,
                Color.appBackground
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }

    private var photoSection: some View {
        ZStack {
            if !user.photoURLs.isEmpty {
                TabView(selection: $currentPhotoIndex) {
                    ForEach(0..<user.photoURLs.count, id: \.self) { index in
                        Button(action: { fullScreenURL = user.photoURLs[index] }) {
                            photoCell(url: user.photoURLs[index])
                        }
                        .buttonStyle(.plain)
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(height: 400)
            } else {
                noPhotoPlaceholder
            }

            if user.photoURLs.count > 1 {
                photoNavigationOverlay
            }
        }
        .padding(.horizontal, 20)
    }

    private func photoCell(url: String) -> some View {
        AsyncImage(url: URL(string: url)) { phase in
            switch phase {
            case .empty:
                photoLoadingPlaceholder
            case .success(let image):
                image
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .frame(height: 400)
                    .background(Color.appBorder.opacity(0.05))
                    .cornerRadius(24)
            case .failure:
                photoErrorPlaceholder
            @unknown default:
                EmptyView()
            }
        }
    }

    private var photoLoadingPlaceholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24)
                .fill(
                    LinearGradient(
                        colors: [
                            Color.appPrimary.opacity(0.2),
                            Color.appPrimary.opacity(0.2)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            ProgressView()
                .tint(Color.appPrimary)
                .scaleEffect(1.5)
        }
        .frame(height: 400)
    }

    private var photoErrorPlaceholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24)
                .fill(
                    LinearGradient(
                        colors: [
                            Color.appPrimary.opacity(0.2),
                            Color.appPrimary.opacity(0.2)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            VStack(spacing: 12) {
                Image(systemName: "photo")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appPrimary.opacity(0.4))
                Text("Photo unavailable")
                    .font(.system(size: 14, weight: .medium, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
            }
        }
        .frame(height: 400)
    }

    private var noPhotoPlaceholder: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24)
                .fill(
                    LinearGradient(
                        colors: [
                            Color.appPrimary.opacity(0.3),
                            Color.appPrimary.opacity(0.3)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Image(systemName: "person.circle.fill")
                .font(.system(size: 120))
                .foregroundColor(Color.appPrimary.opacity(0.5))
        }
        .frame(height: 400)
    }

    private var photoNavigationOverlay: some View {
        ZStack {
            HStack {
                Button {
                    withAnimation {
                        currentPhotoIndex = currentPhotoIndex > 0
                            ? currentPhotoIndex - 1
                            : user.photoURLs.count - 1
                    }
                } label: {
                    Image(systemName: "chevron.left.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.white.opacity(0.8))
                        .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                }
                .padding(.leading, 20)

                Spacer()

                Button {
                    withAnimation {
                        currentPhotoIndex = currentPhotoIndex < user.photoURLs.count - 1
                            ? currentPhotoIndex + 1
                            : 0
                    }
                } label: {
                    Image(systemName: "chevron.right.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.white.opacity(0.8))
                        .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                }
                .padding(.trailing, 20)
            }

            VStack {
                Spacer()
                HStack(spacing: 6) {
                    ForEach(0..<user.photoURLs.count, id: \.self) { index in
                        Circle()
                            .fill(index == currentPhotoIndex ? Color.white : Color.white.opacity(0.5))
                            .frame(width: 8, height: 8)
                    }
                }
                .padding(.vertical, 12)
                .padding(.horizontal, 16)
                .background(Color.black.opacity(0.3))
                .cornerRadius(20)
                .padding(.bottom, 16)
            }
        }
    }

    private var nameAndModeSection: some View {
        VStack(spacing: 8) {
            Text(user.displayName)
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(Color.appPrimaryText)

            HStack(spacing: 12) {
                HStack(spacing: 4) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Color.appPrimary)
                    Text("\(distance) miles away")
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
            }
        }
    }

    private var matchIndicator: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 18))
                .foregroundColor(Color.appPrimary)
            Text("You'd match!")
                .font(.system(size: 16, weight: .semibold, design: .rounded))
                .foregroundColor(Color.appPrimaryText)
            Text("You share interests & availability")
                .font(.system(size: 14, weight: .regular, design: .rounded))
                .foregroundColor(Color.appSecondaryText)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.appPrimary.opacity(0.15))
        .cornerRadius(12)
        .padding(.horizontal, 20)
    }

    private var activitiesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Color.appPrimary)
                Text("Interests")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                if !sharedActivities.isEmpty {
                    Spacer()
                    Text("\(sharedActivities.count) shared")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }

            FlowLayout(spacing: 10) {
                ForEach(user.activities, id: \.id) { activity in
                    activityChip(activity: activity)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCardBackground.opacity(0.7))
        .cornerRadius(20)
        .padding(.horizontal, 20)
    }

    private func activityChip(activity: Activity) -> some View {
        let isShared = sharedActivities.contains(where: { $0.id == activity.id })
        return Text(activity.name)
            .font(.system(size: 15, weight: .semibold, design: .rounded))
            .foregroundColor(
                isShared
                    ? Color.appPrimary
                    : Color.appSecondaryText
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                isShared
                    ? Color.appPrimary.opacity(0.15)
                    : Color.appCardBackground.opacity(0.7)
            )
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        isShared
                            ? Color.appPrimary.opacity(0.3)
                            : Color.clear,
                        lineWidth: 2
                    )
            )
    }

    private var availabilitySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "calendar.badge.clock")
                    .font(.system(size: 18))
                    .foregroundColor(Color.appPrimary)
                Text("Availability")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
                if !sharedTimes.isEmpty {
                    Spacer()
                    Text("\(sharedTimes.count) shared")
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appPrimary)
                }
            }

            FlowLayout(spacing: 10) {
                ForEach(user.daySlotCombos, id: \.self) { combo in
                    timeSlotChip(combo: combo)
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCardBackground.opacity(0.7))
        .cornerRadius(20)
        .padding(.horizontal, 20)
    }

    private func timeSlotChip(combo: DaySlotCombo) -> some View {
        let isShared = sharedTimes.contains(combo)
        return HStack(spacing: 6) {
            Text(combo.timeSlot.icon)
                .font(.system(size: 14))
            Text("\(combo.dayOfWeek.rawValue) \(combo.timeSlot.rawValue)")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
        }
        .foregroundColor(
            isShared
                ? Color.appPrimary
                : Color.appSecondaryText
        )
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            isShared
                ? Color.appPrimary.opacity(0.15)
                : Color.appCardBackground.opacity(0.7)
        )
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    isShared
                        ? Color.appPrimary.opacity(0.3)
                        : Color.clear,
                    lineWidth: 2
                )
        )
    }

    private var matchInfoSection: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: wouldMatch ? "sparkles" : "info.circle.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Color.appPrimary)
                Text(wouldMatch ? "About Matching" : "Why No Match?")
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appTextStrong)
            }

            Text(
                wouldMatch
                    ? "We'll automatically create a match since you share interests and availability. You'll see them in your Matches tab where you can decide to say Yay or Nay!"
                    : "To match, you need to share at least one interest AND one time slot. Keep browsing — you'll find your people!"
            )
            .font(.system(size: 15, weight: .regular, design: .rounded))
            .foregroundColor(Color.appSecondaryText)
            .multilineTextAlignment(.center)
            .lineSpacing(4)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(Color.appPrimary.opacity(0.1))
        .cornerRadius(16)
        .padding(.horizontal, 20)
    }

    private func fullScreenPhotoView(url: String) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()

            AsyncImage(url: URL(string: url)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .ignoresSafeArea()
                case .empty:
                    ProgressView()
                        .tint(.white)
                        .scaleEffect(2)
                case .failure:
                    VStack(spacing: 16) {
                        Image(systemName: "photo")
                            .font(.system(size: 80))
                            .foregroundColor(.white.opacity(0.5))
                        Text("Photo unavailable")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.7))
                    }
                @unknown default:
                    EmptyView()
                }
            }

            VStack {
                HStack {
                    Spacer()
                    Button(action: { fullScreenURL = nil }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 36))
                            .foregroundColor(.white)
                            .shadow(color: .black.opacity(0.5), radius: 4, x: 0, y: 2)
                    }
                    .padding(.top, 16)
                    .padding(.trailing, 20)
                }
                Spacer()
            }
        }
    }

    // MARK: - Helpers

}

// MARK: - Helper Types

private struct FullScreenPhotoURL: Identifiable {
    let id = UUID()
    let url: String
}

// MARK: - Preview

#Preview {
    let user = FirebaseUser(
        id: "user2",
        displayName: "Jordan",
        photoURLs: ["url1", "url2", "url3"],
        activities: [
            Activity(name: "Hiking", isUserAdded: false),
            Activity(name: "Coffee", isUserAdded: false),
            Activity(name: "Board Games", isUserAdded: false)
        ],
        daySlotCombos: [
            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
            DaySlotCombo(dayOfWeek: .friday, timeSlot: .night),
            DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon)
        ],
        latitude: 30.2700,
        longitude: -97.7400,
        isProfileComplete: true
    )

    let currentUser = FirebaseUser(
        id: "user1",
        displayName: "Alex",
        photoURLs: ["url1", "url2", "url3"],
        activities: [
            Activity(name: "Hiking", isUserAdded: false),
            Activity(name: "Yoga", isUserAdded: false),
            Activity(name: "Tacos", isUserAdded: false)
        ],
        daySlotCombos: [
            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
            DaySlotCombo(dayOfWeek: .monday, timeSlot: .evening),
            DaySlotCombo(dayOfWeek: .wednesday, timeSlot: .night)
        ],
        latitude: 30.2672,
        longitude: -97.7431,
        isProfileComplete: true
    )

    UserProfileView(user: user, currentUser: currentUser)
}
