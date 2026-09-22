//
//  MatchDetailView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

/// Detailed view of a match showing full profile information
struct MatchDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let matchWithUser: MatchWithUser
    @ObservedObject var viewModel: MatchesViewModel
    
    @State private var showYayConfirmation = false
    @State private var showNayConfirmation = false
    @State private var showProposePlan = false
    @State private var loadedPhotos: [UIImage] = []
    @State private var selectedPhotoIndex = 0
    @State private var isLoadingPhotos = false
    
    var isPending: Bool {
        matchWithUser.match.isPending(for: viewModel.currentUser?.id ?? "")
    }
    
    /// "3 mi" style distance, or nil if either party has no location on file
    /// (the app's (0, 0) "unset" sentinel).
    var distanceString: String? {
        guard let currentUser = viewModel.currentUser,
              currentUser.latitude != 0 || currentUser.longitude != 0,
              matchWithUser.otherUser.latitude != 0 || matchWithUser.otherUser.longitude != 0
        else { return nil }
        let userLocation = CLLocation(latitude: matchWithUser.otherUser.latitude, longitude: matchWithUser.otherUser.longitude)
        let currentLocation = CLLocation(latitude: currentUser.latitude, longitude: currentUser.longitude)
        let distanceInMiles = currentLocation.distance(from: userLocation) / 1609.34
        return distanceInMiles < 1 ? String(format: "%.1f mi", distanceInMiles) : String(format: "%.0f mi", distanceInMiles)
    }
    
    var body: some View {
        ZStack {
            // Warm gradient background - ALWAYS PRESENT
            LinearGradient(
                colors: [
                    Color.white,
                    Color.white
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            // Always show the content immediately with user data that's already loaded
            ScrollView {
                    VStack(spacing: 24) {
                        // Photo gallery
                        PhotoGalleryView(
                            photos: loadedPhotos,
                            selectedIndex: $selectedPhotoIndex
                        )
                        .padding(.horizontal, 20)
                        
                        // Name and mode
                        VStack(spacing: 8) {
                            Text(matchWithUser.otherUser.displayName)
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundColor(Color.appNavy)
                            
                        }
                        
                        // Match stats
                        if let distanceString {
                            HStack(spacing: 16) {
                                StatBadge(
                                    icon: "location.fill",
                                    value: distanceString,
                                    label: "Away"
                                )
                            }
                            .padding(.horizontal, 20)
                        }
                        
                        // Shared activities
                        if !matchWithUser.sharedActivities.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack(spacing: 8) {
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(Color.appPrimary)
                                    
                                    Text("Shared Interests")
                                        .font(.system(size: 18, weight: .bold, design: .rounded))
                                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                }
                                
                                FlowLayout(spacing: 10) {
                                    ForEach(matchWithUser.sharedActivities, id: \.self) { activity in
                                        Text(activity)
                                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color.appPrimary)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 10)
                                            .background(Color.appPrimary.opacity(0.15))
                                            .cornerRadius(16)
                                    }
                                }
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white.opacity(0.7))
                            .cornerRadius(16)
                            .padding(.horizontal, 20)
                        }

                        // Category-based match reason (only shown when there's no identical
                        // shared activity to explain the match instead)
                        if let categoryMatchLabel = matchWithUser.categoryMatchLabel {
                            HStack(spacing: 8) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 14))
                                    .foregroundColor(Color.appPrimary)

                                Text(categoryMatchLabel)
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            }
                            .padding(.horizontal, 20)
                        }

                        // Shared times
                        if !matchWithUser.sharedTimes.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack(spacing: 8) {
                                    Image(systemName: "calendar.badge.clock")
                                        .font(.system(size: 18))
                                        .foregroundColor(Color.appPrimary)
                                    
                                    Text("Free at the Same Time")
                                        .font(.system(size: 18, weight: .bold, design: .rounded))
                                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                }
                                
                                FlowLayout(spacing: 10) {
                                    ForEach(matchWithUser.sharedTimes, id: \.self) { time in
                                        Text(time)
                                            .font(.system(size: 15, weight: .semibold, design: .rounded))
                                            .foregroundColor(Color.appPrimary)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 10)
                                            .background(Color.appPrimary.opacity(0.15))
                                            .cornerRadius(16)
                                    }
                                }
                            }
                            .padding(20)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white.opacity(0.7))
                            .cornerRadius(16)
                            .padding(.horizontal, 20)
                        }
                        
                        // Action buttons (if pending)
                        if isPending {
                            VStack(spacing: 12) {
                                // Yay button
                                Button {
                                    showYayConfirmation = true
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "hand.thumbsup.fill")
                                            .font(.system(size: 18))
                                        Text("Say Yay")
                                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy,
                                                Color.appNavy
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(16)
                                    .shadow(color: Color.appNavy.opacity(0.3),
                                           radius: 12, x: 0, y: 6)
                                }
                                
                                // Nay button
                                Button {
                                    showNayConfirmation = true
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "hand.thumbsdown.fill")
                                            .font(.system(size: 18))
                                        Text("Say Nay")
                                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(Color.white.opacity(0.9))
                                    .cornerRadius(16)
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.top, 8)
                        }
                        
                        // Connected status (if mutual)
                        if matchWithUser.isMutual {
                            VStack(spacing: 16) {
                                HStack(spacing: 8) {
                                    Image(systemName: "checkmark.seal.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(Color.appPrimary)
                                    
                                    Text("You're Connected!")
                                        .font(.system(size: 18, weight: .bold, design: .rounded))
                                        .foregroundColor(Color.appNavy)
                                }
                                
                                Text("You both said Yay. Start a conversation!")
                                    .font(.system(size: 15, weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                    .multilineTextAlignment(.center)
                                
                                Button {
                                    NotificationCenter.default.post(
                                        name: .navigateToMatchThread,
                                        object: nil,
                                        userInfo: [
                                            "matchID": matchWithUser.match.id,
                                            "otherUserID": matchWithUser.otherUser.id,
                                            "otherUserName": matchWithUser.otherUser.displayName
                                        ]
                                    )
                                    dismiss()
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "message.fill")
                                            .font(.system(size: 18))
                                        Text("Send Message")
                                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(
                                        LinearGradient(
                                            colors: [
                                                Color.appNavy,
                                                Color.appNavy
                                            ],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(16)
                                    .shadow(color: Color.appNavy.opacity(0.3),
                                           radius: 12, x: 0, y: 6)
                                }
                                
                                Button {
                                    showProposePlan = true
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "calendar.badge.plus")
                                            .font(.system(size: 18))
                                        Text("Propose a Plan")
                                            .font(.system(size: 18, weight: .semibold, design: .rounded))
                                    }
                                    .foregroundColor(Color.appPrimary)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 56)
                                    .background(Color.white.opacity(0.9))
                                    .cornerRadius(16)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .strokeBorder(Color.appPrimary.opacity(0.4), lineWidth: 1.5)
                                    )
                                }
                            }
                            .padding(20)
                            .background(Color.appPrimary.opacity(0.1))
                            .cornerRadius(16)
                            .padding(.horizontal, 20)
                        }
                        
                        Spacer()
                            .frame(height: 40)
                    }
                    .padding(.top, 60)
                }
                .scrollIndicators(.hidden)
            
            // Close button overlay
            VStack {
                HStack {
                    Spacer()
                    
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 32))
                            .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                            .symbolRenderingMode(.hierarchical)
                            .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                    }
                    .padding(.top, 16)
                    .padding(.trailing, 20)
                }
                
                Spacer()
            }
        }
        .task {
            print("🚀🚀🚀 MatchDetailView task fired!")
            print("🚀 User: \(matchWithUser.otherUser.displayName)")
            print("🚀 photoURLs: \(matchWithUser.otherUser.photoURLs)")
            await loadPhotos()
        }
        .alert("Say Yay?", isPresented: $showYayConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Yes, Yay!") {
                Task {
                    let _ = await viewModel.sayYay(to: matchWithUser.match)
                    if matchWithUser.match.isMutualMatch {
                        // Show celebration
                    }
                    dismiss()
                }
            }
        } message: {
            Text("This will let \(matchWithUser.otherUser.displayName) know you're interested in being friends.")
        }
        .alert("Say Nay?", isPresented: $showNayConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Yes, Nay", role: .destructive) {
                Task {
                    let _ = await viewModel.sayNay(to: matchWithUser.match)
                    dismiss()
                }
            }
        } message: {
            Text("This match will be removed. \(matchWithUser.otherUser.displayName) won't be notified.")
        }
        .sheet(isPresented: $showProposePlan) {
            ProposePlanSheet(
                matchID: matchWithUser.match.id,
                receiverID: matchWithUser.otherUser.id,
                viewModel: MessagingViewModel()
            )
        }
    }
    
    // MARK: - Photo Loading
    
    private func loadPhotos() async {
        print("🖼️ MatchDetailView: Starting photo load for \(matchWithUser.otherUser.displayName)")
        print("🖼️ Photo URLs count: \(matchWithUser.otherUser.photoURLs.count)")
        
        let photoURLs = matchWithUser.otherUser.photoURLs
        
        // If no photo URLs, finish loading immediately
        guard !photoURLs.isEmpty else {
            print("⚠️ MatchDetailView: No photo URLs found, using placeholder")
            return
        }
        
        // Set loading flag
        await MainActor.run {
            isLoadingPhotos = true
        }
        
        // Load photos from Firebase Storage
        var photos: [UIImage] = []
        
        for (index, urlString) in photoURLs.enumerated() {
            print("📥 MatchDetailView: Loading photo \(index + 1)/\(photoURLs.count) from: \(urlString)")
            do {
                let image = try await FirebaseStorageService.shared.downloadProfilePhoto(from: urlString)
                photos.append(image)
                print("✅ MatchDetailView: Successfully loaded photo \(index + 1)")
            } catch {
                print("❌ MatchDetailView: Failed to load photo \(index + 1) from \(urlString): \(error.localizedDescription)")
                // Continue with remaining photos even if one fails
            }
        }
        
        await MainActor.run {
            print("🖼️ MatchDetailView: Finished loading \(photos.count) photos")
            loadedPhotos = photos
            isLoadingPhotos = false
        }
    }
    
}

// MARK: - Photo Gallery View

struct PhotoGalleryView: View {
    let photos: [UIImage]
    @Binding var selectedIndex: Int
    @State private var fullScreenPhotoIndex: Int? = nil
    
    var body: some View {
        VStack(spacing: 0) {
            if photos.isEmpty {
                // Placeholder when no photos loaded yet
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
                    
                    VStack(spacing: 16) {
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 100))
                            .foregroundColor(Color.appPrimary.opacity(0.5))
                        
                        // Show subtle loading indicator
                        ProgressView()
                            .tint(Color.appPrimary)
                    }
                }
                .frame(height: 400)
            } else {
                // Photo pager
                TabView(selection: $selectedIndex) {
                    ForEach(photos.indices, id: \.self) { index in
                        Button(action: {
                            fullScreenPhotoIndex = index
                        }) {
                            Image(uiImage: photos[index])
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(maxWidth: .infinity)
                                .frame(height: 400)
                                .clipped()
                                .tag(index)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))
                .frame(height: 400)
                .cornerRadius(24)
                .clipped()
            }
        }
        .fullScreenCover(item: Binding(
            get: { fullScreenPhotoIndex.map { FullScreenPhoto(index: $0, photo: photos[$0]) } },
            set: { fullScreenPhotoIndex = $0?.index }
        )) { item in
            ZStack {
                Color.black.ignoresSafeArea()
                
                Image(uiImage: item.photo)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .ignoresSafeArea()
                
                VStack {
                    HStack {
                        Spacer()
                        
                        Button(action: {
                            fullScreenPhotoIndex = nil
                        }) {
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
    }
}

// Helper struct for fullScreenCover
private struct FullScreenPhoto: Identifiable {
    let id = UUID()
    let index: Int
    let photo: UIImage
}

/// Stat badge component
struct StatBadge: View {
    let icon: String
    let value: String
    let label: String
    
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(Color.appPrimary)
            
            Text(value)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
            
            Text(label)
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(Color.white.opacity(0.7))
        .cornerRadius(16)
    }
}

#Preview {
    let viewModel = MatchesViewModel()
    let user = User(
        id: "user2",
        displayName: "Jordan",
        photoURLs: ["url1", "url2", "url3"],
        activities: [
            ActivityModel(name: "Hiking", isUserAdded: false),
            ActivityModel(name: "Coffee", isUserAdded: false)
        ],
        daySlotCombos: [
            DaySlotComboModel(dayOfWeek: .saturday, timeSlot: .wakeUp)
        ],
        latitude: 30.2700,
        longitude: -97.7400,
        isProfileComplete: true
    )
    
    let match = Match(
        user1ID: "user1",
        user2ID: "user2",
        overlappingActivityNames: ["Hiking", "Coffee"],
        overlappingDaySlots: ["Saturday Wake Up"]
    )
    
    let matchWithUser = MatchWithUser(match: match, otherUser: user)
    
    MatchDetailView(matchWithUser: matchWithUser, viewModel: viewModel)
}
