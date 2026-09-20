//
//  NotificationNavigationExample.swift
//  Avenue3
//
//  Example code for handling notification-based navigation in your RootView
//  This is a reference implementation - adapt to your existing navigation structure
//

import SwiftUI

/*
 
 ADD THIS TO YOUR RootView.swift OR MAIN NAVIGATION VIEW
 =======================================================
 
 This example shows how to handle notification tap navigation.
 Adapt this code to work with your existing navigation structure.
 
 */

// MARK: - Example Implementation

struct RootViewNavigationExample: View {
    // Your existing state management
    @State private var selectedTab = 0
    @State private var navigationPath = NavigationPath()
    
    // Notification-driven navigation state
    @State private var matchIDToShow: String?
    @State private var conversationIDToShow: String?
    @State private var planIDToShow: String?
    
    var body: some View {
        TabView(selection: $selectedTab) {
            // Matches Tab
            NavigationStack {
                ExampleMatchesListView()
            }
            .tabItem {
                Label("Matches", systemImage: "person.2.fill")
            }
            .tag(0)
            
            // Group Tab
            NavigationStack {
                Text("Groups Coming Soon")
            }
            .tabItem {
                Label("Groups", systemImage: "figure.3.fill")
            }
            .tag(1)
            
            // Events Tab
            NavigationStack {
                ExamplePlansListView()
            }
            .tabItem {
                Label("Events", systemImage: "calendar.badge.clock")
            }
            .tag(2)
            
            // Plans Tab
            NavigationStack {
                ExamplePlansListView()
            }
            .tabItem {
                Label("Plans", systemImage: "checkmark.circle.fill")
            }
            .tag(3)
            
            // Messages Tab
            NavigationStack {
                ExampleConversationsListView()
            }
            .tabItem {
                Label("Messages", systemImage: "message.fill")
            }
            .tag(4)
            
            // Settings Tab
            NavigationStack {
                Text("Settings")
            }
            .tabItem {
                Label("Settings", systemImage: "gear.circle.fill")
            }
            .tag(5)
        }
        // MARK: - Notification Navigation Handlers
        .onReceive(NotificationCenter.default.publisher(for: .navigateToMatch)) { notification in
            handleMatchNavigation(notification)
        }
        .onReceive(NotificationCenter.default.publisher(for: .navigateToConversation)) { notification in
            handleConversationNavigation(notification)
        }
        .onReceive(NotificationCenter.default.publisher(for: .navigateToPlan)) { notification in
            handlePlanNavigation(notification)
        }
        // Handle the navigation after state updates
        .onChange(of: matchIDToShow) { _, newValue in
            if newValue != nil {
                selectedTab = 0 // Switch to Matches tab
            }
        }
        .onChange(of: conversationIDToShow) { _, newValue in
            if newValue != nil {
                selectedTab = 4 // Switch to Messages tab
            }
        }
        .onChange(of: planIDToShow) { _, newValue in
            if newValue != nil {
                selectedTab = 3 // Switch to Plans tab
            }
        }
    }
    
    // MARK: - Navigation Handlers
    
    private func handleMatchNavigation(_ notification: Notification) {
        guard let matchID = notification.userInfo?["matchID"] as? String else { return }
        
        print("📱 Navigating to match: \(matchID)")
        
        // Set the match ID to show - this will trigger tab change
        matchIDToShow = matchID
        
        // You can also use this to push to a detail view in your MatchesListView
        // by observing matchIDToShow and using .sheet or .navigationDestination
    }
    
    private func handleConversationNavigation(_ notification: Notification) {
        guard let conversationID = notification.userInfo?["conversationID"] as? String else { return }
        
        print("📱 Navigating to conversation: \(conversationID)")
        
        // Set the conversation ID to show
        conversationIDToShow = conversationID
    }
    
    private func handlePlanNavigation(_ notification: Notification) {
        guard let planID = notification.userInfo?["planID"] as? String else { return }
        
        print("📱 Navigating to plan: \(planID)")
        
        // Set the plan ID to show
        planIDToShow = planID
    }
}

// MARK: - Alternative Approach: Navigation Coordinator

/*
 
 ALTERNATIVE: Use a Navigation Coordinator Pattern
 ==================================================
 
 If your app uses a more complex navigation structure,
 consider using a navigation coordinator:
 
 Tab indices for 6-tab layout:
 matches  = 0
 group    = 1
 events   = 2
 plans    = 3
 messages = 4
 settings = 5
 
 */

@MainActor
@Observable
class NavigationCoordinator {
    var selectedTab: Int = 0
    var matchToShow: String?
    var conversationToShow: String?
    var planToShow: String?
    
    init() {
        setupNotificationObservers()
    }
    
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            forName: .navigateToMatch,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            // Extract data before entering @Sendable closure
            guard let matchID = notification.userInfo?["matchID"] as? String else { return }
            Task { @MainActor [weak self] in
                self?.selectedTab = 0
                self?.matchToShow = matchID
            }
        }
        
        NotificationCenter.default.addObserver(
            forName: .navigateToConversation,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            // Extract data before entering @Sendable closure
            guard let conversationID = notification.userInfo?["conversationID"] as? String else { return }
            Task { @MainActor [weak self] in
                self?.selectedTab = 4
                self?.conversationToShow = conversationID
            }
        }
        
        NotificationCenter.default.addObserver(
            forName: .navigateToPlan,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            // Extract data before entering @Sendable closure
            guard let planID = notification.userInfo?["planID"] as? String else { return }
            Task { @MainActor [weak self] in
                self?.selectedTab = 3
                self?.planToShow = planID
            }
        }
    }

}

// MARK: - Using the Coordinator

struct RootViewWithCoordinator: View {
    @State private var coordinator = NavigationCoordinator()
    
    var body: some View {
        TabView(selection: $coordinator.selectedTab) {
            // Your tabs here
            NavigationStack {
                ExampleMatchesListView()
                    .sheet(item: Binding(
                        get: { coordinator.matchToShow.map { MatchIdentifier(id: $0) } },
                        set: { coordinator.matchToShow = $0?.id }
                    )) { matchIdentifier in
                        ExampleMatchDetailView(matchID: matchIdentifier.id)
                    }
            }
            .tabItem {
                Label("Matches", systemImage: "person.2.fill")
            }
            .tag(0)
            
            // Other tabs...
        }
    }
}

// Helper for Identifiable navigation
struct MatchIdentifier: Identifiable {
    let id: String
}

// MARK: - Placeholder Views
// NOTE: These are example placeholders - replace with your actual views

struct ExampleHomeView: View {
    var body: some View {
        Text("Home View")
    }
}

struct ExampleMatchesListView: View {
    var body: some View {
        Text("Matches List")
    }
}

struct ExampleConversationsListView: View {
    var body: some View {
        Text("Conversations List")
    }
}

struct ExamplePlansListView: View {
    var body: some View {
        Text("Plans List")
    }
}

struct ExampleMatchDetailView: View {
    let matchID: String
    
    var body: some View {
        Text("Match Detail: \(matchID)")
    }
}

/*
 
 QUICK INTEGRATION STEPS
 ========================
 
 1. Choose your approach:
    - Simple: Add .onReceive handlers directly to your RootView TabView
    - Complex: Use the NavigationCoordinator pattern
 
 2. Add @State variables for navigation:
    @State private var matchIDToShow: String?
    @State private var conversationIDToShow: String?
    @State private var planIDToShow: String?
 
 3. Add .onReceive handlers for the three notification types:
    .onReceive(NotificationCenter.default.publisher(for: .navigateToMatch))
    .onReceive(NotificationCenter.default.publisher(for: .navigateToConversation))
    .onReceive(NotificationCenter.default.publisher(for: .navigateToPlan))
 
 4. Implement navigation logic:
    - Switch to the appropriate tab
    - Show the detail view using .sheet, .navigationDestination, or .fullScreenCover
 
 5. Clear the navigation state after presentation to avoid showing it again
 
 6. Test by tapping notifications!
 
 */
