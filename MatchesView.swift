//
//  MatchesView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI

/// Matches tab showing pending matches and connected (mutual) matches
struct MatchesView: View {
    @StateObject private var viewModel = MatchesViewModel()
    @State private var isLoading = true
    @State private var selectedMatch: MatchWithUser?
    @State private var planTarget: MatchWithUser?
    @State private var newlyConnectedMatch: MatchWithUser?
    @State private var hasAppeared = false
    
    // Convert matches to MatchWithUser for display
    private var pendingMatchesWithUsers: [MatchWithUser] {
        viewModel.pendingMatches.compactMap { match -> MatchWithUser? in
            guard let firebaseUser = viewModel.getUser(for: match) else { return nil }
            return MatchWithUser(
                match: match,
                otherUser: firebaseUser.toUser(),
                currentUser: viewModel.currentUser,
                isOtherUserSharingLocation: firebaseUser.isSharingLocation
            )
        }
    }

    private var mutualMatchesWithUsers: [MatchWithUser] {
        viewModel.mutualMatches.compactMap { match -> MatchWithUser? in
            guard let firebaseUser = viewModel.getUser(for: match) else { return nil }
            return MatchWithUser(
                match: match,
                otherUser: firebaseUser.toUser(),
                currentUser: viewModel.currentUser,
                isOtherUserSharingLocation: firebaseUser.isSharingLocation
            )
        }
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Mutual match celebration overlay
                if newlyConnectedMatch != nil {
                    celebrationOverlay
                }
                

                Color.appBackground
                    .ignoresSafeArea()

                if isLoading {
                    // Loading state
                    VStack(spacing: 20) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.2)
                        
                        Text("Finding your matches...")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    }
                } else if pendingMatchesWithUsers.isEmpty && mutualMatchesWithUsers.isEmpty {
                    // Empty state - wrapped in ScrollView for pull-to-refresh
                    GeometryReader { geometry in
                        ScrollView {
                            VStack {
                                Spacer()
                                EmptyMatchesView()
                                Spacer()
                            }
                            .frame(minHeight: geometry.size.height)
                        }
                        .refreshable {
                            await viewModel.refresh()
                        }
                    }
                } else {
                    // Matches content
                    ScrollView {
                        VStack(spacing: 24) {
                            // Pending Matches Section
                            if !pendingMatchesWithUsers.isEmpty {
                                VStack(alignment: .leading, spacing: 16) {
                                    // Section header
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Pending Matches")
                                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                                .foregroundColor(Color.appPrimaryText)
                                            
                                            Text("Say Yay to connect")
                                                .font(.system(size: 14, weight: .regular, design: .rounded))
                                                .foregroundColor(Color.appSecondaryText)
                                        }
                                        
                                        Spacer()
                                        
                                        // Count badge
                                        Text("\(pendingMatchesWithUsers.count)")
                                            .font(.system(size: 14, weight: .bold, design: .rounded))
                                            .foregroundColor(.white)
                                            .frame(minWidth: 28, minHeight: 28)
                                            .background(
                                                Circle()
                                                    .fill(
                                                        LinearGradient(
                                                            colors: [
                                                                Color.appPrimary,
                                                                Color.appPrimary
                                                            ],
                                                            startPoint: .topLeading,
                                                            endPoint: .bottomTrailing
                                                        )
                                                    )
                                            )
                                    }
                                    .padding(.horizontal, 20)
                                    
                                    // Pending match cards
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 16) {
                                            ForEach(pendingMatchesWithUsers) { matchWithUser in
                                                PendingMatchCard(
                                                    matchWithUser: matchWithUser,
                                                    onYay: {
                                                        handleYay(matchWithUser.match)
                                                    },
                                                    onNay: {
                                                        handleNay(matchWithUser.match)
                                                    },
                                                    onTap: {
                                                        selectedMatch = matchWithUser
                                                    }
                                                )
                                                .frame(width: 300)
                                            }
                                        }
                                        .padding(.horizontal, 20)
                                    }
                                }
                            }
                            
                            // Connected Matches Section
                            if !mutualMatchesWithUsers.isEmpty {
                                VStack(alignment: .leading, spacing: 16) {
                                    // Section header
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("Connected")
                                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                                .foregroundColor(Color.appPrimaryText)
                                            
                                            Text("You've both said Yay")
                                                .font(.system(size: 14, weight: .regular, design: .rounded))
                                                .foregroundColor(Color.appSecondaryText)
                                        }
                                        
                                        Spacer()
                                        
                                        // Count badge
                                        Text("\(mutualMatchesWithUsers.count)")
                                            .font(.system(size: 14, weight: .bold, design: .rounded))
                                            .foregroundColor(.white)
                                            .frame(minWidth: 28, minHeight: 28)
                                            .background(
                                                Circle()
                                                    .fill(
                                                        LinearGradient(
                                                            colors: [
                                                                Color.appPrimary,
                                                                Color.appPrimary
                                                            ],
                                                            startPoint: .topLeading,
                                                            endPoint: .bottomTrailing
                                                        )
                                                    )
                                            )
                                    }
                                    .padding(.horizontal, 20)
                                    
                                    // Connected match rows
                                    VStack(spacing: 12) {
                                        ForEach(mutualMatchesWithUsers) { matchWithUser in
                                            ConnectedMatchRow(
                                                matchWithUser: matchWithUser,
                                                showUpMeter: viewModel.getUser(for: matchWithUser.match)?.showUpMeter ?? "New",
                                                simpaticoScore: viewModel.getSimpatico(for: matchWithUser.match),
                                                onMessage: {
                                                    handleMessage(matchWithUser)
                                                },
                                                onPlan: {
                                                    handlePlan(matchWithUser)
                                                },
                                                onTap: {
                                                    selectedMatch = matchWithUser
                                                }
                                            )
                                        }
                                    }
                                    .padding(.horizontal, 20)
                                }
                            }
                            
                            Spacer()
                                .frame(height: 40)
                        }
                        .padding(.top, 20)
                    }
                    .refreshable {
                        await viewModel.refresh()
                    }
                }
            }
            .navigationTitle("Matches")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .top) {
                if !mutualMatchesWithUsers.isEmpty {
                    connectedAvatarStrip
                }
            }
            .sheet(item: $selectedMatch) { match in
                MatchDetailView(matchWithUser: match, viewModel: viewModel)
            }
            .sheet(item: $planTarget) { matchWithUser in
                ProposePlanSheet(
                    matchID: matchWithUser.match.id,
                    receiverID: matchWithUser.otherUser.id,
                    viewModel: MessagingViewModel()
                )
            }
        }
        .task {
            await loadMatches()
        }
        // "Stay Connected!" — asks for notification permission once, right after the first
        // mutual match (MatchesViewModel sets this; see FIRST_MATCH_NOTIFICATION_REQUEST.md).
        .overlay {
            if viewModel.showNotificationPrompt {
                NotificationPermissionPromptView(
                    onEnable: { Task { await viewModel.requestNotificationPermission() } },
                    onDismiss: { viewModel.dismissNotificationPrompt() }
                )
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.showNotificationPrompt)
        .onAppear {
            if hasAppeared {
                Task { await viewModel.refreshMatchedUsers() }
            }
            hasAppeared = true
        }
        .onChange(of: mutualMatchesWithUsers.count) { oldValue, newValue in
            if hasAppeared && newValue > oldValue && !mutualMatchesWithUsers.isEmpty {
                newlyConnectedMatch = mutualMatchesWithUsers.last
                
                // Auto-dismiss after 6 seconds
                Task {
                    try? await Task.sleep(for: .seconds(6))
                    newlyConnectedMatch = nil
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .onboardingCompleted)) { _ in
            Task {
                isLoading = true
                await loadMatches()
            }
        }
    }
    
    // MARK: - Connected Avatar Strip
    
    private var connectedAvatarStrip: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text("Connected")
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 16) {
                        ForEach(mutualMatchesWithUsers) { matchWithUser in
                            Button(action: {
                                selectedMatch = matchWithUser
                            }) {
                                VStack(spacing: 6) {
                                    if let photoURL = matchWithUser.otherUser.photoURLs.first {
                                        AsyncImage(url: URL(string: photoURL)) { phase in
                                            switch phase {
                                            case .success(let image):
                                                image
                                                    .resizable()
                                                    .aspectRatio(contentMode: .fill)
                                                    .frame(width: 44, height: 44)
                                                    .clipShape(Circle())
                                            case .failure(_), .empty:
                                                Circle()
                                                    .fill(Color.appPrimary)
                                                    .frame(width: 44, height: 44)
                                                    .overlay(
                                                        Image(systemName: "person.fill")
                                                            .font(.system(size: 20))
                                                            .foregroundColor(.white)
                                                    )
                                            @unknown default:
                                                Circle()
                                                    .fill(Color.appPrimary)
                                                    .frame(width: 44, height: 44)
                                            }
                                        }
                                    } else {
                                        Circle()
                                            .fill(Color.appPrimary)
                                            .frame(width: 44, height: 44)
                                            .overlay(
                                                Image(systemName: "person.fill")
                                                    .font(.system(size: 20))
                                                    .foregroundColor(.white)
                                            )
                                    }
                                    
                                    Text(matchWithUser.otherUser.displayName)
                                        .font(.system(size: 11, design: .rounded))
                                        .foregroundColor(Color.appPrimaryText)
                                        .lineLimit(1)
                                        .frame(width: 60)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.trailing, 20)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.appCardBackground)
            .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)
        }
    }
    
    // MARK: - Celebration Overlay
    
    private var celebrationOverlay: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                        newlyConnectedMatch = nil
                    }
                }
            
            if let matchWithUser = newlyConnectedMatch {
                VStack(spacing: 20) {
                    Text("🎉")
                        .font(.system(size: 48))
                    
                    Text("You're connected!")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    
                    Text(matchWithUser.otherUser.displayName)
                        .font(.system(size: 17, weight: .medium, design: .rounded))
                        .foregroundColor(Color.appPrimaryText)
                    
                    Text("Go say hi 👋")
                        .font(.system(size: 15, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                    
                    VStack(spacing: 12) {
                        Button(action: {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                                selectedMatch = newlyConnectedMatch
                                newlyConnectedMatch = nil
                            }
                        }) {
                            Text("Open")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary,
                                            Color.appPrimary
                                        ],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .cornerRadius(14)
                        }
                        
                        Button(action: {
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                                newlyConnectedMatch = nil
                            }
                        }) {
                            Text("Maybe later")
                                .font(.system(size: 15, design: .rounded))
                                .foregroundColor(Color.appSecondaryText)
                        }
                    }
                    .padding(.top, 8)
                }
                .padding(32)
                .background(Color.appCardBackground)
                .cornerRadius(28)
                .shadow(color: .black.opacity(0.2), radius: 20, x: 0, y: 10)
                .padding(.horizontal, 40)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.75), value: newlyConnectedMatch)
    }
    
    // MARK: - Actions
    
    private func loadMatches() async {
        await viewModel.loadCurrentUser()
        await viewModel.createPotentialMatches()
        await viewModel.loadMatches()
        isLoading = false
    }
    
    private func handleYay(_ match: Match) {
        Task {
            let success = await viewModel.sayYay(to: match)
            if success && match.isMutualMatch {
                // Show celebration for mutual match
                // Could add haptics or confetti animation here
            }
        }
    }
    
    private func handleNay(_ match: Match) {
        Task {
            await viewModel.sayNay(to: match)
        }
    }
    
    private func handleMessage(_ matchWithUser: MatchWithUser) {
        NotificationCenter.default.post(
            name: .navigateToMatchThread,
            object: nil,
            userInfo: [
                "matchID": matchWithUser.match.id,
                "otherUserID": matchWithUser.otherUser.id,
                "otherUserName": matchWithUser.otherUser.displayName
            ]
        )
    }
    
    private func handlePlan(_ matchWithUser: MatchWithUser) {
        planTarget = matchWithUser
    }
}

// MARK: - Pending Match Card

struct PendingMatchCard: View {
    let matchWithUser: MatchWithUser
    let onYay: () -> Void
    let onNay: () -> Void
    let onTap: () -> Void

    @State private var cardPhoto: UIImage? = nil
    @State private var isLoadingPhoto = false

    var body: some View {
        VStack(spacing: 0) {
            // Tappable photo + info section
            Button(action: onTap) {
                VStack(spacing: 0) {
                    // Photo area
                    ZStack {
                        Rectangle()
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
                            .frame(width: 300, height: 200)

                        if let photo = cardPhoto {
                            Image(uiImage: photo)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 300, height: 200)
                                .clipped()
                                .allowsHitTesting(false)
                        } else {
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 80))
                                .foregroundColor(Color.appPrimary.opacity(0.5))

                            if isLoadingPhoto {
                                ProgressView()
                                    .tint(Color.appPrimary)
                                    .scaleEffect(1.2)
                                    .offset(y: 50)
                            }
                        }
                    }
                    .frame(width: 300, height: 200)
                    .clipped()
                    .task {
                        guard let firstURL = matchWithUser.otherUser.photoURLs.first else { return }
                        isLoadingPhoto = true
                        if let image = try? await FirebaseStorageService.shared.downloadProfilePhoto(from: firstURL) {
                            cardPhoto = image
                        }
                        isLoadingPhoto = false
                    }

                    // Info section
                    VStack(alignment: .leading, spacing: 12) {
                        Text(matchWithUser.otherUser.displayName)
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)

                        if let distanceText = matchWithUser.distanceText {
                            HStack(spacing: 4) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color.appPrimary)
                                Text(distanceText)
                                    .font(.system(size: 13, weight: .medium, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                            }
                        }

                        Divider()

                        if !matchWithUser.sharedActivities.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 6) {
                                    Image(systemName: "heart.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.appPrimary)
                                    Text("Shared Interests")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                }
                                FlowLayout(spacing: 6) {
                                    ForEach(matchWithUser.sharedActivities, id: \.self) { activity in
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
                        }

                        if let categoryMatchLabel = matchWithUser.categoryMatchLabel {
                            HStack(spacing: 6) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                Text(categoryMatchLabel)
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                            }
                        }

                        if !matchWithUser.sharedTimes.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 6) {
                                    Image(systemName: "calendar.badge.clock")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color.appPrimary)
                                    Text("Free at the same time")
                                        .font(.system(size: 13, weight: .semibold, design: .rounded))
                                        .foregroundColor(Color.appSecondaryText)
                                }
                                FlowLayout(spacing: 6) {
                                    ForEach(matchWithUser.sharedTimes, id: \.self) { time in
                                        Text(time)
                                            .font(.system(size: 12, weight: .medium, design: .rounded))
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
                    .frame(width: 300, alignment: .leading)
                }
            }
            .buttonStyle(.plain)
            .contentShape(Rectangle())

            // Yay / Nay buttons
            HStack(spacing: 12) {
                Button(action: onNay) {
                    HStack(spacing: 6) {
                        Image(systemName: "hand.thumbsdown.fill")
                            .font(.system(size: 16))
                        Text("Nay")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(Color.appSecondaryText)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.appBorder.opacity(0.5))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)

                Button(action: onYay) {
                    HStack(spacing: 6) {
                        Image(systemName: "hand.thumbsup.fill")
                            .font(.system(size: 16))
                        Text("Yay")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(
                        LinearGradient(
                            colors: [
                                Color.appPrimary,
                                Color.appPrimary
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(width: 300)
        }
        .background(Color.appCardBackground)
        .cornerRadius(20)
        .shadow(color: .black.opacity(0.08), radius: 12, x: 0, y: 4)
    }
}

// MARK: - Connected Match Row

struct ConnectedMatchRow: View {
    let matchWithUser: MatchWithUser
    let showUpMeter: String
    let simpaticoScore: Int?
    let onMessage: () -> Void
    let onPlan: () -> Void
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Photo placeholder
                ZStack {
                    Circle()
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
                        .frame(width: 60, height: 60)

                    if let photoURL = matchWithUser.otherUser.photoURLs.first {
                        AsyncImage(url: URL(string: photoURL)) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 60, height: 60)
                                    .clipShape(Circle())
                            default:
                                Image(systemName: "person.circle.fill")
                                    .font(.system(size: 40))
                                    .foregroundColor(Color.appPrimary.opacity(0.5))
                            }
                        }
                    } else {
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 40))
                            .foregroundColor(Color.appPrimary.opacity(0.5))
                    }
                }
                .frame(width: 60, height: 60)
                
                // Info
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(matchWithUser.otherUser.displayName)
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundColor(Color.appPrimaryText)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Color.appPrimary)
                            .fixedSize()
                    }
                    
                    if let firstActivity = matchWithUser.sharedActivities.first {
                        Text(firstActivity + (matchWithUser.sharedActivities.count > 1 ? " +\(matchWithUser.sharedActivities.count - 1) more" : ""))
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    } else if let categoryMatchLabel = matchWithUser.categoryMatchLabel {
                        Text(categoryMatchLabel)
                            .font(.system(size: 14, weight: .regular, design: .rounded))
                            .foregroundColor(Color.appSecondaryText)
                    }

                    FlowLayout(spacing: 8) {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar.badge.plus")
                                .font(.system(size: 11))
                                .foregroundColor(Color.appPrimary)
                            Text(showUpMeter)
                                .font(.system(size: 12, weight: .semibold, design: .rounded))
                                .foregroundColor(Color.appPrimary)
                                .lineLimit(1)
                        }

                        if let score = simpaticoScore {
                            HStack(spacing: 3) {
                                Image(systemName: "face.smiling.fill")
                                    .font(.system(size: 10, weight: .semibold))
                                Text("\(score)%")
                                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                                    .lineLimit(1)
                            }
                            .foregroundColor(Color.appPrimary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.appPrimary.opacity(0.12))
                            .clipShape(Capsule())
                        }

                        if let distanceText = matchWithUser.distanceText {
                            HStack(spacing: 4) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color.appSecondaryText)
                                Text(distanceText.replacingOccurrences(of: " away", with: ""))
                                    .font(.system(size: 12, weight: .medium, design: .rounded))
                                    .foregroundColor(Color.appSecondaryText)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Plan button
                Button(action: onPlan) {
                    Image(systemName: "calendar.badge.plus")
                        .font(.system(size: 20))
                        .foregroundColor(Color.appPrimary)
                        .frame(width: 44, height: 44)
                        .background(Color.appPrimary.opacity(0.15))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                
                // Message button
                Button(action: onMessage) {
                    Image(systemName: "message.fill")
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(
                            Circle()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.appPrimary,
                                            Color.appPrimary
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        )
                        .shadow(color: Color.appPrimary.opacity(0.3),
                               radius: 6, x: 0, y: 3)
                }
                .buttonStyle(.plain)
            }
            .padding(16)
            .background(Color.appCardBackground)
            .cornerRadius(16)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Empty State

struct EmptyMatchesView: View {
    var body: some View {
        VStack(spacing: 24) {
            ZStack {
                Circle()
                    .fill(Color.appPrimary.opacity(0.2))
                    .frame(width: 120, height: 120)
                
                Image(systemName: "heart.circle")
                    .font(.system(size: 60))
                    .foregroundColor(Color.appPrimary)
            }
            
            VStack(spacing: 12) {
                Text("No Matches Yet")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appPrimaryText)
                
                Text("We're looking for people who share\nyour interests and availability.\nCheck back soon!")
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
                    
                    Text("How Matching Works")
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(Color.appSecondaryText)
                }
                
                Text("We automatically find people nearby who share at least one activity AND one time slot with you.")
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
    }
}

#Preview("Matches View") {
    MainTabView()
}

#Preview("Empty State") {
    NavigationStack {
        ZStack {
            Color.appBackground
                .ignoresSafeArea()

            EmptyMatchesView()
        }
        .navigationTitle("Matches")
    }
}
