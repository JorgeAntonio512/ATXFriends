//
//  HomeView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

/// Home/Discovery tab showing nearby users
struct HomeView: View {
    @State private var viewModel = HomeViewModel()
    @State private var selectedUser: FirebaseUser?
    @State private var showUserProfile = false
    
    // Radius options
    private let radiusOptions: [Double] = [5, 10, 15, 25]
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Warm gradient background
                LinearGradient(
                    colors: [
                        Color.appBackground,
                        Color.appBackground
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Radius selector
                    VStack(spacing: 12) {
                        HStack {
                            Image(systemName: "location.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Color.appPrimary)
                            
                            Text("Search Radius")
                                .font(.system(size: 16, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextStrong)
                            
                            Spacer()
                            
                            Text("\(Int(viewModel.selectedRadius)) miles")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appPrimary)
                        }
                        
                        // Radius buttons
                        HStack(spacing: 8) {
                            ForEach(radiusOptions, id: \.self) { radius in
                                Button {
                                    viewModel.selectedRadius = radius
                                    Task {
                                        await viewModel.loadNearbyUsers()
                                    }
                                } label: {
                                    Text("\(Int(radius))")
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                        .foregroundColor(
                                            viewModel.selectedRadius == radius ?
                                            .white :
                                            Color.appPrimary
                                        )
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 36)
                                        .background(
                                            viewModel.selectedRadius == radius ?
                                            LinearGradient(
                                                colors: [
                                                    Color.appPrimary,
                                                    Color.appPrimary
                                                ],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            ) :
                                            LinearGradient(
                                                colors: [Color.appCardBackground.opacity(0.7), Color.appCardBackground.opacity(0.7)],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                        )
                                        .cornerRadius(10)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                    .background(Color.appCardBackground.opacity(0.6))
                    .shadow(color: .black.opacity(0.05), radius: 8, x: 0, y: 4)
                    
                    // User cards
                    if viewModel.isLoading {
                        // Loading state
                        VStack(spacing: 20) {
                            ProgressView()
                                .tint(Color.appPrimary)
                                .scaleEffect(1.2)
                            
                            Text("Finding people nearby...")
                                .font(.system(size: 16, weight: .medium, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                        }
                        .frame(maxHeight: .infinity)
                    } else if viewModel.nearbyUsers.isEmpty {
                        // Empty state
                        EmptyDiscoveryView(radius: viewModel.selectedRadius)
                    } else {
                        // User cards
                        ScrollView {
                            LazyVStack(spacing: 16) {
                                ForEach(viewModel.nearbyUsers) { user in
                                    DiscoveryUserCard(
                                        user: user,
                                        currentUser: viewModel.currentUser,
                                        onTap: {
                                            selectedUser = user
                                            showUserProfile = true
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 20)
                        }
                        .refreshable {
                            await viewModel.loadNearbyUsers()
                        }
                    }
                }
            }
            .navigationTitle("Discover")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showUserProfile) {
                if let user: FirebaseUser = selectedUser {
                    UserProfileView(user: user, currentUser: viewModel.currentUser)
                }
            }
        }
        .task {
            await viewModel.loadCurrentUser()
            await viewModel.loadNearbyUsers()
        }
    }
}

// MARK: - Discovery User Card

struct DiscoveryUserCard: View {
    let user: FirebaseUser
    let currentUser: FirebaseUser?
    let onTap: () -> Void
    
    // Calculate shared items
    private var sharedActivities: [String] {
        guard let current = currentUser else { return [] }
        let currentActivityIDs = Set(current.activities.map { $0.id })
        return user.activities.filter { currentActivityIDs.contains($0.id) }.map { $0.name }
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
        return String(format: "%.1f mi away", distanceInMiles)
    }
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                // Photo section
                ZStack {
                    if let firstPhotoURL = user.photoURLs.first, !firstPhotoURL.isEmpty {
                        AsyncImage(url: URL(string: firstPhotoURL)) { phase in
                            switch phase {
                            case .empty:
                                RoundedRectangle(cornerRadius: 20)
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
                                    .frame(height: 200)
                                    .overlay {
                                        ProgressView()
                                            .tint(Color.appPrimary)
                                    }
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(height: 200)
                                    .clipped()
                                    .cornerRadius(20)
                            case .failure:
                                RoundedRectangle(cornerRadius: 20)
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
                                    .frame(height: 200)
                                    .overlay {
                                        Image(systemName: "person.circle.fill")
                                            .font(.system(size: 80))
                                            .foregroundColor(Color.appPrimary.opacity(0.5))
                                    }
                            @unknown default:
                                RoundedRectangle(cornerRadius: 20)
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
                                    .frame(height: 200)
                            }
                        }
                    } else {
                        // Fallback for no photo URL
                        RoundedRectangle(cornerRadius: 20)
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
                            .frame(height: 200)
                            .overlay {
                                Image(systemName: "person.circle.fill")
                                    .font(.system(size: 80))
                                    .foregroundColor(Color.appPrimary.opacity(0.5))
                            }
                    }
                }
                
                // Info section
                VStack(alignment: .leading, spacing: 12) {
                    // Name, mode, distance
                    VStack(alignment: .leading, spacing: 6) {
                        Text(user.displayName)
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appTextStrong)
                        
                        HStack(spacing: 12) {
                            HStack(spacing: 4) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text(distance)
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                            }
                        }
                    }
                    
                    Divider()
                    
                    // Shared activities
                    if !sharedActivities.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 4) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("Shared Interests")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appTextBody)
                            }
                            
                            FlowLayout(spacing: 6) {
                                ForEach(sharedActivities, id: \.self) { activity in
                                    Text(activity)
                                        .font(.system(size: 12, weight: .medium, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.appPrimary.opacity(0.15))
                                        .cornerRadius(12)
                                }
                            }
                        }
                    } else {
                        // Show all activities if no shared ones
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Interests")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appTextBody)

                            FlowLayout(spacing: 6) {
                                ForEach(user.activities, id: \.id) { activity in
                                    Text(activity.name)
                                        .font(.system(size: 12, weight: .medium, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.appCardBackground.opacity(0.6))
                                        .cornerRadius(12)
                                }
                            }
                        }
                    }
                    
                    // Shared times
                    if !sharedTimes.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 4) {
                                Image(systemName: "calendar.badge.clock")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("Free at the same time")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appTextBody)
                            }
                            
                            FlowLayout(spacing: 6) {
                                ForEach(sharedTimes, id: \.self) { combo in
                                    HStack(spacing: 4) {
                                        Text(combo.timeSlot.icon)
                                            .font(.system(size: 10))
                                        
                                        Text("\(combo.dayOfWeek.rawValue) \(combo.timeSlot.rawValue)")
                                            .font(.system(size: 11, weight: .medium, design: .rounded))
                                    }
                                    .foregroundColor(Color.appPrimary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Color.appPrimary.opacity(0.15))
                                    .cornerRadius(12)
                                }
                            }
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color.appCardBackground.opacity(0.9))
            .cornerRadius(20)
            .shadow(color: .black.opacity(0.1), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(.plain)
    }
    

}

// MARK: - Empty State

struct EmptyDiscoveryView: View {
    let radius: Double
    
    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 120, height: 120)
                
                Image(systemName: "person.2.circle")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appPrimary)
            }
            
            VStack(spacing: 12) {
                Text("No One Nearby")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
                
                Text("No one found within \(Int(radius)) miles.\nTry increasing your search radius!")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 40)
            
            // Info box
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                        .foregroundColor(Color.appPrimary)
                    
                    Text("How It Works")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appTextBody)
                }
                
                Text("We show people nearby who have completed their profiles. When you find someone interesting, we'll create a match if you share activities and availability!")
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color.appSecondaryText)
                    .lineSpacing(2)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.appPrimary.opacity(0.1))
            .cornerRadius(12)
            .padding(.horizontal, 32)
        }
        .frame(maxHeight: .infinity)
    }
}

#Preview("Home View") {
    MainTabView()
}

#Preview("Empty State") {
    NavigationStack {
        ZStack {
            LinearGradient(
                colors: [
                    Color.appBackground,
                    Color.appBackground
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            EmptyDiscoveryView(radius: 10)
        }
        .navigationTitle("Discover")
    }
}
