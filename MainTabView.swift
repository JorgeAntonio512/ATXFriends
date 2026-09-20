//
//  MainTabView.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import SwiftUI
import CoreLocation

extension Notification.Name {
    static let navigateToMatchThread = Notification.Name("navigateToMatchThread")
    static let navigateToSimpatico = Notification.Name("navigateToSimpatico")
    static let showUpReportSubmitted = Notification.Name("showUpReportSubmitted")
}

// MARK: - Scroll Offset Preference Key

struct ScrollOffsetPreferenceKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

// MARK: - Custom Group Tab Icon

/// Custom 3-person icon drawn with SF Symbols layering
struct GroupTabIcon: View {
    var body: some View {
        HStack(spacing: -5) {
            Image(systemName: "person.fill")
                .font(.system(size: 11))
            Image(systemName: "person.fill")
                .font(.system(size: 16))   // center person is tallest
            Image(systemName: "person.fill")
                .font(.system(size: 11))
        }
        .frame(width: 28, height: 24)
    }
}

// MARK: - Group View (Placeholder)

// The GroupView has been moved to its own file: GroupView.swift

// MARK: - Simpatico Placeholder View

private struct SimpaticoPlaceholderView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color.white,
                    Color.white
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                ZStack {
                    Circle()
                        .fill(Color.appPrimary.opacity(0.2))
                        .frame(width: 120, height: 120)

                    Image(systemName: "face.smiling.fill")
                        .font(.system(size: 60))
                        .foregroundColor(Color.appPrimary)
                }

                VStack(spacing: 12) {
                    Text("Simpatico")
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)

                    Text("Coming soon")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
            }
        }
    }
}

// MARK: - Tab Enum

enum Tab: Int, CaseIterable {
    case matches   = 0
    case today     = 1
    case simpatico = 2
    case messages  = 3
    case settings  = 4
}

/// Main tab view for authenticated users
/// Five tabs: Matches, Today, Simpatico, Messages, Settings
struct MainTabView: View {
    @State private var selectedTab: Tab = .matches
    @StateObject private var unreadState = UnreadState.shared
    @State private var tabBarOpacity: Double = 1.0
    @State private var lastScrollY: Double = 0
    @State private var threadToOpen: MessageThread?
    
    static let tabBarHeight: CGFloat = 50  // content area, not including safe area

    var body: some View {
        let tabBarHeight = Self.tabBarHeight
        ZStack {
            // Content switching based on selected tab
            SwiftUI.Group {
                switch selectedTab {
                case .matches:
                    MatchesView()
                        .safeAreaInset(edge: .bottom) {
                            Color.clear.frame(height: tabBarHeight)
                        }
                case .today:
                    TodayView()
                        .safeAreaInset(edge: .bottom) {
                            Color.clear.frame(height: tabBarHeight)
                        }
                case .simpatico:
                    SimpaticoView()
                        .safeAreaInset(edge: .bottom) {
                            Color.clear.frame(height: tabBarHeight)
                        }
                case .messages:
                    MessagesListView()
                        .safeAreaInset(edge: .bottom) {
                            Color.clear.frame(height: tabBarHeight)
                        }
                case .settings:
                    SettingsTabView()
                        .safeAreaInset(edge: .bottom) {
                            Color.clear.frame(height: tabBarHeight)
                        }
                }
            }
            
            // Custom tab bar at bottom
            VStack {
                Spacer()
                CustomTabBar(
                    selectedTab: $selectedTab,
                    unreadState: unreadState,
                    tabBarOpacity: tabBarOpacity
                )
            }
        }
        .onAppear {
            if let userID = FirebaseAuthService.shared.currentUserID {
                UnreadState.shared.startListening(userID: userID)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .navigateToMatchThread)) { notification in
            guard
                let matchID = notification.userInfo?["matchID"] as? String,
                let otherUserID = notification.userInfo?["otherUserID"] as? String,
                let otherUserName = notification.userInfo?["otherUserName"] as? String
            else { return }

            // Build a minimal thread to open
            let match = Match(
                id: matchID,
                user1ID: "",
                user2ID: otherUserID,
                isMutualMatch: true
            )
            let otherUser = User(
                id: otherUserID,
                displayName: otherUserName,
                photoURLs: [],
                activities: [],
                daySlotCombos: [],
                latitude: 0,
                longitude: 0,
                isProfileComplete: true
            )
            threadToOpen = MessageThread(
                id: matchID,
                match: match,
                otherUser: otherUser,
                lastMessage: nil,
                unreadCount: 0
            )
            selectedTab = .messages
        }
        .fullScreenCover(item: $threadToOpen) { thread in
            MessageThreadView(thread: thread, viewModel: MessagingViewModel())
        }
        .onPreferenceChange(ScrollOffsetPreferenceKey.self) { value in
            let delta = value - lastScrollY
            
            if delta > 10 {
                // Scrolling down
                withAnimation(.easeInOut(duration: 0.25)) {
                    tabBarOpacity = 0
                }
            } else if delta < 0 {
                // Scrolling up
                withAnimation(.easeInOut(duration: 0.25)) {
                    tabBarOpacity = 1
                }
            }
            
            lastScrollY = value
        }
    }
}

// MARK: - Custom Tab Bar

struct CustomTabBar: View {
    @Binding var selectedTab: Tab
    var unreadState: UnreadState
    var tabBarOpacity: Double
    
    var body: some View {
        GeometryReader { geometry in
            let tabWidth = geometry.size.width / CGFloat(Tab.allCases.count)
            let safeAreaBottomInset = geometry.safeAreaInsets.bottom
            
            VStack(spacing: 0) {
                // Sliding indicator bar at top
                ZStack(alignment: .leading) {
                    // Full-width divider line (light gray)
                    Rectangle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(height: 1)
                    
                    // Sage green active indicator
                    Rectangle()
                        .fill(Color.appPrimary)
                        .frame(width: tabWidth, height: 3)
                        .cornerRadius(1.5)
                        .offset(x: tabWidth * CGFloat(selectedTab.rawValue))
                        .animation(.spring(response: 0.3, dampingFraction: 0.75), value: selectedTab)
                }
                .frame(height: 3)
                
                // Tab items HStack
                HStack(spacing: 0) {
                    // Matches Tab
                    CustomTabItem(
                        tab: .matches,
                        selectedTab: $selectedTab,
                        label: "Matches",
                        selectedIcon: "person.2.fill",
                        unselectedIcon: "person.2",
                        hasBadge: false
                    )
                    
                    // Today Tab
                    CustomTabItem(
                        tab: .today,
                        selectedTab: $selectedTab,
                        label: "Today",
                        selectedIcon: "calendar.circle.fill",
                        unselectedIcon: "calendar.circle",
                        hasBadge: false
                    )
                    
                    // Simpatico Tab
                    CustomTabItem(
                        tab: .simpatico,
                        selectedTab: $selectedTab,
                        label: "Simpatico",
                        selectedIcon: "face.smiling.fill",
                        unselectedIcon: "face.smiling",
                        hasBadge: false
                    )
                    
                    // Messages Tab
                    CustomTabItem(
                        tab: .messages,
                        selectedTab: $selectedTab,
                        label: "Messages",
                        selectedIcon: "message.fill",
                        unselectedIcon: "message",
                        hasBadge: unreadState.hasUnreadMessages
                    )
                    
                    // Settings Tab
                    CustomTabItem(
                        tab: .settings,
                        selectedTab: $selectedTab,
                        label: "Settings",
                        selectedIcon: "gear.circle.fill",
                        unselectedIcon: "gear.circle",
                        hasBadge: false
                    )
                }
                .padding(.top, 8)
                .padding(.bottom, safeAreaBottomInset)
            }
            .background(Color(UIColor.systemBackground))
        }
        .frame(height: 50)
        .opacity(tabBarOpacity)
        .animation(.easeInOut(duration: 0.25), value: tabBarOpacity)
    }
}

// MARK: - Custom Tab Item

struct CustomTabItem<CustomIcon: View>: View {
    let tab: Tab
    @Binding var selectedTab: Tab
    let label: String
    var selectedIcon: String?
    var unselectedIcon: String?
    var customIcon: CustomIcon?
    var hasBadge: Bool
    
    init(
        tab: Tab,
        selectedTab: Binding<Tab>,
        label: String,
        selectedIcon: String,
        unselectedIcon: String,
        hasBadge: Bool
    ) where CustomIcon == EmptyView {
        self.tab = tab
        self._selectedTab = selectedTab
        self.label = label
        self.selectedIcon = selectedIcon
        self.unselectedIcon = unselectedIcon
        self.customIcon = nil
        self.hasBadge = hasBadge
    }
    
    init(
        tab: Tab,
        selectedTab: Binding<Tab>,
        label: String,
        customIcon: CustomIcon,
        hasBadge: Bool
    ) {
        self.tab = tab
        self._selectedTab = selectedTab
        self.label = label
        self.selectedIcon = nil
        self.unselectedIcon = nil
        self.customIcon = customIcon
        self.hasBadge = hasBadge
    }
    
    var isSelected: Bool {
        selectedTab == tab
    }
    
    var body: some View {
        Button {
            selectedTab = tab
        } label: {
            VStack(spacing: 4) {
                ZStack(alignment: .topTrailing) {
                    // Icon
                    if let customIcon = customIcon {
                        customIcon
                            .foregroundColor(isSelected ? Color.appPrimary : .gray)
                    } else if let selectedIcon = selectedIcon, let unselectedIcon = unselectedIcon {
                        Image(systemName: isSelected ? selectedIcon : unselectedIcon)
                            .font(.system(size: 22))
                            .frame(width: 28, height: 24)
                            .foregroundColor(isSelected ? Color.appPrimary : .gray)
                    }
                    
                    // Badge dot
                    if hasBadge {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 8, height: 8)
                            .offset(x: 12, y: -4)
                    }
                }
                
                // Label
                Text(label)
                    .font(.system(size: 10, weight: .medium, design: .rounded))
                    .multilineTextAlignment(.center)
                    .foregroundColor(isSelected ? Color.appPrimary : .gray)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}




// MARK: - Matches Tab

struct MatchesTabView: View {
    @StateObject private var viewModel = MatchesViewModel()
    @State private var selectedMatch: Match?
    @State private var showMatchProfile = false
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Warm gradient background
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                if viewModel.isLoading || viewModel.isCreatingMatches {
                    // Loading state
                    VStack(spacing: 20) {
                        ProgressView()
                            .tint(Color.appPrimary)
                            .scaleEffect(1.2)
                        
                        Text(viewModel.isCreatingMatches ? "Finding matches..." : "Loading matches...")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    }
                } else if viewModel.pendingMatches.isEmpty {
                    // Empty state
                    NoMatchesYetView()
                } else {
                    // Matches list
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            ForEach(viewModel.pendingMatches, id: \.id) { match in
                                if let user = viewModel.getUser(for: match) {
                                    MatchCard(
                                        match: match,
                                        user: user,
                                        currentUser: viewModel.currentUser,
                                        onYay: {
                                            Task {
                                                _ = await viewModel.sayYay(to: match)
                                            }
                                        },
                                        onNay: {
                                            Task {
                                                _ = await viewModel.sayNay(to: match)
                                            }
                                        },
                                        onTap: {
                                            selectedMatch = match
                                            showMatchProfile = true
                                        }
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 20)
                    }
                    .refreshable {
                        await viewModel.refresh()
                    }
                }
            }
            .navigationTitle("Matches")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showMatchProfile) {
                if let match = selectedMatch,
                   let user = viewModel.getUser(for: match) {
                    UserProfileView(
                        user: user,
                        currentUser: viewModel.currentUser
                    )
                }
            }
        }
        .task {
            await viewModel.loadCurrentUser()
            await viewModel.createPotentialMatches()
            await viewModel.loadMatches()
        }
        .overlay {
            if viewModel.showNotificationPrompt {
                NotificationPermissionPromptView(
                    onEnable: {
                        Task {
                            await viewModel.requestNotificationPermission()
                        }
                    },
                    onDismiss: {
                        viewModel.dismissNotificationPrompt()
                    }
                )
            }
        }
    }
}

// MARK: - Notification Permission Prompt

struct NotificationPermissionPromptView: View {
    let onEnable: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        ZStack {
            // Dimmed background
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    // Tapping outside doesn't dismiss
                }
            
            // Prompt card
            VStack(spacing: 24) {
                // Icon
                ZStack {
                    Circle()
                        .fill(Color.appPrimary.opacity(0.2))
                        .frame(width: 80, height: 80)
                    
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 36))
                        .foregroundColor(Color.appPrimary)
                }
                
                // Title and message
                VStack(spacing: 12) {
                    Text("Stay Connected!")
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(Color.appNavy)
                    
                    Text("You have a new match! 🎉\n\nEnable notifications so you never miss a message or plan from your new friends.")
                        .font(.system(size: 16, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                }
                .padding(.horizontal, 8)
                
                // Buttons
                VStack(spacing: 12) {
                    // Primary button - Enable Notifications
                    Button {
                        onEnable()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "bell.badge")
                                .font(.system(size: 16, weight: .semibold))
                            Text("Enable Notifications")
                                .font(.system(size: 17, weight: .semibold, design: .rounded))
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
                        .shadow(color: Color.appNavy.opacity(0.3), radius: 12, x: 0, y: 6)
                    }
                    .buttonStyle(.plain)
                    
                    // Secondary button - Not Now
                    Button {
                        onDismiss()
                    } label: {
                        Text("Not Now")
                            .font(.system(size: 16, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(32)
            .background(
                RoundedRectangle(cornerRadius: 24)
                    .fill(Color.white)
            )
            .shadow(color: .black.opacity(0.2), radius: 20, x: 0, y: 10)
            .padding(.horizontal, 40)
        }
        .transition(.opacity)
    }
}

// MARK: - Match Card

struct MatchCard: View {
    let match: Match
    let user: FirebaseUser
    let currentUser: FirebaseUser?
    let onYay: () -> Void
    let onNay: () -> Void
    let onTap: () -> Void
    
    private var distance: String {
        guard let current = currentUser else { return "" }
        let userLocation = CLLocation(latitude: user.latitude, longitude: user.longitude)
        let currentLocation = CLLocation(latitude: current.latitude, longitude: current.longitude)
        let distanceInMeters = currentLocation.distance(from: userLocation)
        let distanceInMiles = distanceInMeters / 1609.34
        return String(format: "%.1f mi away", distanceInMiles)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Photo and user info
            Button(action: onTap) {
                VStack(spacing: 0) {
                    // Photo
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
                    
                    // User info
                    VStack(alignment: .leading, spacing: 12) {
                        // Name and distance
                        VStack(alignment: .leading, spacing: 6) {
                            Text(user.displayName)
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                            
                            HStack(spacing: 4) {
                                Image(systemName: "location.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text(distance)
                                    .font(.system(size: 14, weight: .medium, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                            }
                        }
                        
                        Divider()
                        
                        // Shared activities
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 4) {
                                Image(systemName: "heart.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("Shared Interests")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            }
                            
                            FlowLayout(spacing: 6) {
                                ForEach(match.overlappingActivityNames, id: \.self) { activity in
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
                        
                        // Shared times
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 4) {
                                Image(systemName: "calendar.badge.clock")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.appPrimary)
                                
                                Text("Free at the same time")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.40, green: 0.40, blue: 0.40))
                            }
                            
                            FlowLayout(spacing: 6) {
                                ForEach(match.overlappingDaySlots, id: \.self) { timeSlot in
                                    Text(timeSlot)
                                        .font(.system(size: 11, weight: .medium, design: .rounded))
                                        .foregroundColor(Color.appPrimary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.appPrimary.opacity(0.15))
                                        .cornerRadius(12)
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
            .buttonStyle(.plain)
            
            // Decision buttons
            HStack(spacing: 16) {
                // Nay button
                Button(action: onNay) {
                    HStack(spacing: 8) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .semibold))
                        Text("Nay")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.white.opacity(0.8))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
                
                // Yay button
                Button(action: onYay) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 18, weight: .semibold))
                        Text("Yay")
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
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
                    .cornerRadius(12)
                    .shadow(color: Color.appNavy.opacity(0.3), radius: 8, x: 0, y: 4)
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.white)
                .opacity(0.9)
        )
        .shadow(color: Color.black.opacity(0.1), radius: 12, x: 0, y: 6)
    }
}

// MARK: - Empty Matches View

struct NoMatchesYetView: View {
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
                Text("No Matches Yet")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Color.appNavy)
                
                Text("We're looking for people nearby who share your interests and availability.\n\n")
                    .font(.system(size: 16, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            .padding(.horizontal, 40)
        }
        .frame(maxHeight: .infinity)
    }
}

// MARK: - Settings Tab (Placeholder)

struct SettingsTabView: View {
    @State private var authViewModel = AuthViewModel()
    @State private var profileViewModel = ProfileViewModel()
    @State private var showSignOutAlert = false
    @EnvironmentObject var notificationManager: NotificationManager
    
    /// Get current location permission status badge text
    private var locationStatusBadge: String {
        let manager = CLLocationManager()
        let status = manager.authorizationStatus
        
        switch status {
        case .authorizedWhenInUse, .authorizedAlways:
            // Check accuracy authorization
            if #available(iOS 14.0, *) {
                switch manager.accuracyAuthorization {
                case .fullAccuracy:
                    return "Precise"
                case .reducedAccuracy:
                    return "Approximate"
                @unknown default:
                    return "Approximate"
                }
            } else {
                return "Enabled"
            }
        case .denied, .restricted:
            return "Denied"
        case .notDetermined:
            return "Not Set"
        @unknown default:
            return "Unknown"
        }
    }
    

    
    var body: some View {
        NavigationStack {
            ZStack {
                // Warm gradient background
                LinearGradient(
                    colors: [
                        Color.white,
                        Color.white
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 0) {
                        // Settings List
                        VStack(spacing: 16) {
                            // Profile Section
                            SettingsCard(title: "Profile") {
                                VStack(spacing: 12) {
                                    NavigationLink {
                                        EditProfileView(viewModel: profileViewModel)
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "person.circle.fill",
                                            title: "Display Name",
                                            subtitle: "Update your display name"
                                        )
                                    }
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    NavigationLink {
                                        PhotosSettingsView(viewModel: profileViewModel)
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "photo.fill",
                                            title: "Photos",
                                            subtitle: "Manage your 3 photos"
                                        )
                                    }
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    NavigationLink {
                                        ActivitiesSettingsView(viewModel: profileViewModel)
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "heart.fill",
                                            title: "Activities",
                                            subtitle: "Your 3 interests"
                                        )
                                    }
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    NavigationLink {
                                        AvailabilitySettingsView(viewModel: profileViewModel)
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "calendar.badge.clock",
                                            title: "Availability",
                                            subtitle: "Your 3 time slots"
                                        )
                                    }
                                }
                            }
                            
                            // Preferences Section
                            SettingsCard(title: "Preferences") {
                                NavigationLink {
                                    SearchRadiusSettingsView(viewModel: profileViewModel)
                                } label: {
                                    SettingsRowLabel(
                                        icon: "location.circle.fill",
                                        title: "Search Radius",
                                        subtitle: "\(Int(profileViewModel.radiusMiles)) miles"
                                    )
                                }
                            }
                            
                            // Account Section
                            SettingsCard(title: "Account") {
                                VStack(spacing: 12) {
                                    NavigationLink {
                                        NotificationSettingsView()
                                            .environmentObject(notificationManager)
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "bell.fill",
                                            title: "Notifications",
                                            subtitle: "Manage alerts"
                                        )
                                    }
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    NavigationLink {
                                        PrivacyAndSafetyView()
                                    } label: {
                                        SettingsRowLabel(
                                            icon: "hand.raised.fill",
                                            title: "Privacy & Safety",
                                            subtitle: "Blocking, reporting, and data"
                                        )
                                    }
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    // Location Settings
                                    Button {
                                        if let url = URL(string: UIApplication.openSettingsURLString) {
                                            UIApplication.shared.open(url)
                                        }
                                    } label: {
                                        HStack(spacing: 12) {
                                            Image(systemName: "location.fill")
                                                .font(.system(size: 20))
                                                .foregroundColor(Color.appPrimary)
                                                .frame(width: 32)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("Location")
                                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                                                
                                                Text("Manage location access")
                                                    .font(.system(size: 14, weight: .regular, design: .rounded))
                                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                            }
                                            
                                            Spacer()
                                            
                                            // Status badge
                                            Text(locationStatusBadge)
                                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                                                .foregroundColor(
                                                    locationStatusBadge == "Denied" ? 
                                                    Color(red: 0.85, green: 0.45, blue: 0.40) :
                                                    locationStatusBadge == "Not Set" ?
                                                    Color(red: 0.60, green: 0.60, blue: 0.60) :
                                                    Color.appPrimary
                                                )
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 4)
                                                .background(
                                                    locationStatusBadge == "Denied" ?
                                                    Color(red: 0.85, green: 0.45, blue: 0.40).opacity(0.1) :
                                                    locationStatusBadge == "Not Set" ?
                                                    Color(red: 0.60, green: 0.60, blue: 0.60).opacity(0.1) :
                                                    Color.appPrimary.opacity(0.1)
                                                )
                                                .cornerRadius(8)
                                            
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
                                        }
                                        .padding(.vertical, 8)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                    
                                    Divider()
                                        .padding(.leading, 44)
                                    
                                    // Sign Out Button
                                    Button {
                                        print("🟣 Sign Out tapped")
                                        showSignOutAlert = true
                                    } label: {
                                        HStack(spacing: 12) {
                                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                                .font(.system(size: 20))
                                                .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                                .frame(width: 32)
                                            
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("Sign Out")
                                                    .font(.system(size: 16, weight: .medium, design: .rounded))
                                                    .foregroundColor(Color(red: 0.85, green: 0.45, blue: 0.40))
                                            }
                                            
                                            Spacer()
                                        }
                                        .padding(.vertical, 8)
                                        .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            
                            // App Info
                            VStack(spacing: 4) {
                                Text("ATX Friends")
                                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                                
                                Text("Version 1.0.0")
                                    .font(.system(size: 12, weight: .regular, design: .rounded))
                                    .foregroundColor(Color(red: 0.60, green: 0.60, blue: 0.60))
                            }
                            .padding(.top, 20)
                            .padding(.bottom, 40)
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 20)
                    }
                }
                .scrollIndicators(.hidden)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
        }
        .task {
            await profileViewModel.loadUserProfile()
            await profileViewModel.loadActivities()
        }
        .alert("Sign Out", isPresented: $showSignOutAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Sign Out", role: .destructive) {
                print("✅ User confirmed sign out")
                _ = authViewModel.signOut()
            }
        } message: {
            Text("Are you sure you want to sign out?")
        }
        .onAppear {
            print("🟣 SettingsTabView appeared")
        }
    }
}

// MARK: - Supporting Views

/// Info row for matches tab
struct MatchInfoRow: View {
    let icon: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(Color.appPrimary)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                Text(description)
                    .font(.system(size: 13, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
            }
            
            Spacer()
        }
        .padding()
        .background(Color.white.opacity(0.5))
        .cornerRadius(12)
    }
}

/// Card container for settings sections
struct SettingsCard<Content: View>: View {
    let title: String
    let content: Content
    
    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                .textCase(.uppercase)
                .padding(.leading, 4)
            
            VStack(spacing: 0) {
                content
            }
            .padding()
            .background(Color.white.opacity(0.6))
            .cornerRadius(16)
        }
    }
}

/// Individual setting row
struct SettingsRow: View {
    let icon: String
    let title: String
    let subtitle: String
    var action: (() -> Void)? = nil
    
    var body: some View {
        Button {
            print("🟣 SettingsRow tapped: \(title)")
            action?()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(Color.appPrimary)
                    .frame(width: 32)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                    
                    Text(subtitle)
                        .font(.system(size: 14, weight: .regular, design: .rounded))
                        .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle()) // Make entire row tappable
        }
        .buttonStyle(.plain)
    }
}

/// Settings row label for use in NavigationLinks
struct SettingsRowLabel: View {
    let icon: String
    let title: String
    let subtitle: String
    var showChevron: Bool = false
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(Color.appPrimary)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium, design: .rounded))
                    .foregroundColor(Color(red: 0.35, green: 0.35, blue: 0.35))
                
                Text(subtitle)
                    .font(.system(size: 14, weight: .regular, design: .rounded))
                    .foregroundColor(Color(red: 0.50, green: 0.50, blue: 0.50))
            }
            
            Spacer()
            
            if showChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(red: 0.70, green: 0.70, blue: 0.70))
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}

#Preview("Main Tab View") {
    MainTabView()
}

#Preview("Matches Tab") {
    MatchesTabView()
}

#Preview("Settings Tab") {
    SettingsTabView()
}
