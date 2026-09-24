//
//  MatchesViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/7/26.
//

import Foundation
import CoreLocation
import SwiftUI
import Combine

/// ViewModel for managing matches and match decisions
final class MatchesViewModel: ObservableObject {
    // MARK: - Published State
    
    /// Current user (Firebase representation)
    @Published var currentUser: FirebaseUser?
    
    /// Matches pending decision from current user
    @Published var pendingMatches: [Match] = []
    
    /// Matches where current user said Yay (includes mutual and non-mutual)
    @Published var yayMatches: [Match] = []
    
    /// Mutual matches (both users said Yay)
    @Published var mutualMatches: [Match] = []
    
    /// All matches for the current user
    @Published var allMatches: [Match] = []
    
    /// Users corresponding to the matches (keyed by user ID, Firebase representation)
    @Published var matchedUsers: [String: FirebaseUser] = [:]

    /// Simpatico compatibility scores for mutual matches, keyed by the other user's ID.
    /// Absent means neither user has finished the questionnaire yet.
    @Published var simpaticoScores: [String: Int] = [:]

    /// Error message to display
    @Published var errorMessage: String?
    
    /// Whether data is loading
    @Published var isLoading: Bool = false
    
    /// Whether matches are being created
    @Published var isCreatingMatches: Bool = false
    
    /// Whether to show the notification permission prompt
    @Published var showNotificationPrompt: Bool = false
    
    // MARK: - Private Properties
    
    /// UserDefaults key for tracking if user has been asked for notifications
    private let hasBeenAskedForNotificationsKey = "hasBeenAskedForNotifications"
    
    /// NotificationCenter observer for userBlocked events
    private var userBlockedObserver: Any?

    /// NotificationCenter observer for showUpReportSubmitted events
    private var showUpReportSubmittedObserver: Any?
    
    // MARK: - Services
    
    private let firestoreService = FirestoreService.shared
    private let matchingService = MatchingService.shared
    private let authService = FirebaseAuthService.shared
    
    // MARK: - Initialization
    
    init() {
        setupNotificationObservers()
    }
    
    deinit {
        if let observer = userBlockedObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        if let observer = showUpReportSubmittedObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }
    
    // MARK: - Notification Observers
    
    private func setupNotificationObservers() {
        userBlockedObserver = NotificationCenter.default.addObserver(
            forName: NSNotification.Name("userBlocked"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            print("🚫 MatchesViewModel: Received userBlocked notification, refreshing user data and matches...")
            Task { @MainActor in
                // Re-fetch current user from Firestore to get updated blockedUsers array
                await self?.loadCurrentUser()

                // Now reload matches with the fresh blocked users list
                await self?.loadMatches()
            }
        }

        showUpReportSubmittedObserver = NotificationCenter.default.addObserver(
            forName: .showUpReportSubmitted,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            print("⭐ MatchesViewModel: Received showUpReportSubmitted, refreshing matched user profiles...")
            Task { @MainActor in
                await self?.refreshMatchedUsers()
            }
        }
    }
    
    // MARK: - Load Current User
    
    /// Loads the current user's profile
    @MainActor
    func loadCurrentUser() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        do {
            currentUser = try await firestoreService.fetchUser(userID: userID)
            
            // Debug logging for blocked users
            if let blockedUsers = currentUser?.blockedUsers {
                print("🚫 MatchesViewModel: Loaded current user, blockedUsers array from Firestore: \(blockedUsers)")
                print("🚫 MatchesViewModel: Total blocked users count: \(blockedUsers.count)")
            } else {
                print("🚫 MatchesViewModel: Loaded current user, no blockedUsers array found")
            }
        } catch {
            errorMessage = "Failed to load user profile: \(error.localizedDescription)"
            print("❌ MatchesViewModel: Error loading current user: \(error)")
        }
    }
    
    // MARK: - Load Matches
    
    /// Loads all matches for the current user and categorizes them
    @MainActor
    func loadMatches() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        isLoading = true
        defer { isLoading = false }
        
        do {
            // Always re-fetch current user to get the latest blockedUsers array
            // (This is especially important when called from the userBlocked notification)
            print("🔍 MatchesViewModel: Re-fetching current user to get latest blocked users list...")
            await loadCurrentUser()
            
            let blockedUserIDs = Set(currentUser?.blockedUsers ?? [])
            print("🚫 MatchesViewModel: Using blockedUsers array with \(blockedUserIDs.count) blocked user(s)")
            
            // Fetch all matches for the current user
            let allFetchedMatches = try await firestoreService.fetchMatches(for: userID)
            
            // Filter out matches with blocked users
            let matches = allFetchedMatches.filter { match in
                guard let otherUserID = match.otherUserID(for: userID) else { return false }
                let isBlocked = blockedUserIDs.contains(otherUserID)
                if isBlocked {
                    print("🚫 MatchesViewModel: Filtering out match with blocked user: \(otherUserID)")
                }
                return !isBlocked
            }
            
            allMatches = matches

            print("📥 MatchesViewModel: Loaded \(allFetchedMatches.count) total matches, \(matches.count) after filtering \(blockedUserIDs.count) blocked user(s)")

            // Load user data for all matches before categorizing, so the live-overlap
            // check below has each match partner's current profile to compare against.
            await loadMatchedUserProfiles()

            // Hide (don't delete) matches whose users no longer share any activity or
            // time slot under their *current* profiles. The Match doc and any message
            // thread are untouched — this is a display filter only, so editing a profile
            // back to overlapping again brings the match right back.
            let liveMatches = matches.filter { match in
                guard let otherUserID = match.otherUserID(for: userID),
                      let otherUser = matchedUsers[otherUserID],
                      let current = currentUser else {
                    // Other user's profile isn't loaded (e.g. fetch failed) — don't hide.
                    return true
                }
                let stillOverlaps = matchingService.shouldMatch(user1: current, user2: otherUser)
                if !stillOverlaps {
                    print("👻 MatchesViewModel: Hiding match with \(otherUser.displayName) — no more shared activity/time overlap")
                }
                return stillOverlaps
            }

            // Categorize matches
            pendingMatches = liveMatches.filter { match in
                match.isPending(for: userID) && !match.isRejected
            }

            yayMatches = liveMatches.filter { match in
                match.decision(for: userID) == true
            }

            mutualMatches = liveMatches.filter { match in
                match.isMutualMatch
            }

            print("⏳ MatchesViewModel: \(pendingMatches.count) pending decisions")
            print("💚 MatchesViewModel: \(yayMatches.count) you said Yay to")
            print("🎉 MatchesViewModel: \(mutualMatches.count) mutual matches")

            // Simpatico scores are only relevant for mutual matches, which are now categorized.
            await refreshSimpaticoScores()

        } catch {
            errorMessage = "Failed to load matches: \(error.localizedDescription)"
            print("❌ MatchesViewModel: Error loading matches: \(error)")
        }
    }
    
    /// Loads user profiles for all matched users. Called before categorizing matches so
    /// the live-overlap check in loadMatches() has each match partner's current profile.
    @MainActor
    private func loadMatchedUserProfiles() async {
        guard let currentUserID = authService.currentUserID else { return }

        // Get all unique user IDs from matches
        let userIDs = Set(allMatches.compactMap { match in
            match.otherUserID(for: currentUserID)
        })

        print("👥 MatchesViewModel: Loading \(userIDs.count) matched user profiles...")

        // Fetch each user
        for userID in userIDs {
            do {
                if let user = try await firestoreService.fetchUser(userID: userID) {
                    matchedUsers[userID] = user
                }
            } catch {
                print("❌ MatchesViewModel: Failed to load user \(userID): \(error)")
            }
        }

        print("✅ MatchesViewModel: Loaded \(matchedUsers.count) user profiles")
    }

    /// Refreshes Simpatico scores for the current mutualMatches. Must run after matches
    /// are categorized, since it reads mutualMatches.
    @MainActor
    private func refreshSimpaticoScores() async {
        guard let currentUserID = authService.currentUserID else { return }

        for match in mutualMatches {
            guard let otherUserID = match.otherUserID(for: currentUserID) else { continue }
            if let score = try? await SimpaticoService.shared.fetchScore(
                userID: currentUserID,
                friendID: otherUserID
            ) {
                simpaticoScores[otherUserID] = score
            } else {
                simpaticoScores.removeValue(forKey: otherUserID)
            }
        }
    }
    
    // MARK: - Create Matches
    
    /// Finds nearby compatible users and creates matches in Firestore
    @MainActor
    func createPotentialMatches() async {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return
        }
        
        guard let currentUser = currentUser else {
            errorMessage = "Current user profile not loaded."
            return
        }
        
        guard let location = CLLocationCoordinate2D(
            latitude: currentUser.latitude,
            longitude: currentUser.longitude
        ) as CLLocationCoordinate2D? else {
            errorMessage = "Location not available."
            return
        }
        
        isCreatingMatches = true
        defer { isCreatingMatches = false }
        
        do {
            print("🔍 MatchesViewModel: Finding nearby users within \(currentUser.radiusMiles) miles...")
            
            // Get blocked users list
            let blockedUserIDs = Set(currentUser.blockedUsers)
            print("🚫 MatchesViewModel: User has \(blockedUserIDs.count) blocked users")
            
            // Fetch nearby users from Firestore
            let allNearbyUsers = try await firestoreService.fetchNearbyUsers(
                center: location,
                radiusMiles: currentUser.radiusMiles,
                excludeUserID: userID
            )
            
            print("📍 MatchesViewModel: Found \(allNearbyUsers.count) nearby users")
            
            // Filter out blocked users
            let nearbyUsers = allNearbyUsers.filter { user in
                let isBlocked = blockedUserIDs.contains(user.id)
                if isBlocked {
                    print("🚫 MatchesViewModel: Skipping blocked user: \(user.displayName)")
                }
                return !isBlocked
            }
            
            print("📍 MatchesViewModel: \(nearbyUsers.count) nearby users after filtering blocked users")
            
            // Filter to only completed profiles
            let completedProfiles = nearbyUsers.filter { $0.isProfileComplete }
            print("✅ MatchesViewModel: \(completedProfiles.count) have completed profiles")
            
            // Get existing matches to avoid duplicates
            let existingMatches = try await firestoreService.fetchMatches(for: userID)
            let existingMatchUserIDs = Set(existingMatches.compactMap { match in
                match.otherUserID(for: userID)
            })
            
            print("📋 MatchesViewModel: Already have \(existingMatches.count) existing matches")
            
            // Find users we should match with (share activities AND time slots)
            var newMatchesCreated = 0
            
            for user in completedProfiles {
                // Skip if we already have a match with this user
                guard !existingMatchUserIDs.contains(user.id) else {
                    continue
                }
                
                // createMatch already guards on matchingService.shouldMatch(...) internally,
                // which covers exact activity overlap OR shared activity category, AND time overlap.
                if let match = matchingService.createMatch(between: currentUser, and: user) {
                    do {
                        try await firestoreService.createMatch(match)
                        newMatchesCreated += 1

                        print("💚 MatchesViewModel: Created match with \(user.displayName)")
                        print("   Shared activities: \(match.overlappingActivityNames.joined(separator: ", "))")
                        print("   Shared categories: \(match.overlappingCategoryNames.joined(separator: ", "))")
                        print("   Shared times: \(match.overlappingDaySlots.joined(separator: ", "))")
                    } catch {
                        print("❌ MatchesViewModel: Failed to create match with \(user.displayName): \(error)")
                    }
                }
            }
            
            print("🎉 MatchesViewModel: Created \(newMatchesCreated) new matches")
            
        } catch {
            errorMessage = "Failed to create matches: \(error.localizedDescription)"
            print("❌ MatchesViewModel: Error creating matches: \(error)")
        }
    }
    
    // MARK: - Match Decisions
    
    /// Makes a Yay/Nay decision on a match
    @MainActor
    func makeDecision(on match: Match, decision: Bool) async -> Bool {
        guard let userID = authService.currentUserID else {
            errorMessage = "No user is signed in."
            return false
        }
        
        // Get other user's ID
        guard let otherUserID = match.otherUserID(for: userID) else {
            print("❌ MatchesViewModel: Could not determine other user ID")
            return false
        }
        
        // Check if this will create a mutual match
        let wasNotMutual = !match.isMutualMatch
        let otherUserDecision = match.decision(for: otherUserID)
        let willBeMutual = decision && otherUserDecision == true
        
        print("ℹ️ MatchesViewModel: Making decision on match")
        print("   Current user decision: \(decision ? "Yay" : "Nay")")
        print("   Other user decision: \(otherUserDecision.map { $0 ? "Yay" : "Nay" } ?? "Pending")")
        print("   Will be mutual: \(willBeMutual)")
        
        // Update the match locally
        let updatedMatch = match
        updatedMatch.setDecision(for: userID, decision: decision)
        
        // Verify isMutualMatch was updated correctly
        print("   isMutualMatch after setDecision: \(updatedMatch.isMutualMatch)")
        
        do {
            // Save to Firestore
            try await firestoreService.updateMatch(updatedMatch)
            
            print("✅ MatchesViewModel: Saved decision (\(decision ? "Yay" : "Nay")) for match")
            print("   isMutualMatch saved to Firestore: \(updatedMatch.isMutualMatch)")
            
            // Update local state
            if let index = allMatches.firstIndex(where: { $0.id == match.id }) {
                allMatches[index] = updatedMatch
            }
            
            // Check if this created a new mutual match
            if wasNotMutual && willBeMutual {
                print("🎉 MatchesViewModel: New mutual match created!")
                await checkAndRequestNotificationPermission()
            }
            
            // Reload matches to update categories
            await loadMatches()
            
            return true
        } catch {
            errorMessage = "Failed to save decision: \(error.localizedDescription)"
            print("❌ MatchesViewModel: Error saving decision: \(error)")
            return false
        }
    }
    
    // MARK: - Notification Permission
    
    /// Checks if we should request notification permission and shows prompt if appropriate
    @MainActor
    private func checkAndRequestNotificationPermission() async {
        // Check if we've already asked
        let hasBeenAsked = UserDefaults.standard.bool(forKey: hasBeenAskedForNotificationsKey)
        guard !hasBeenAsked else {
            print("ℹ️ MatchesViewModel: User has already been asked for notification permission")
            return
        }
        
        // Check current notification permission status
        let notificationManager = NotificationManager.shared
        notificationManager.checkNotificationPermissionStatus()
        
        // Only ask if status is .notDetermined
        guard notificationManager.notificationPermissionStatus == .notDetermined else {
            print("ℹ️ MatchesViewModel: Notification permission already determined")
            return
        }
        
        print("📱 MatchesViewModel: Showing notification permission prompt")
        showNotificationPrompt = true
    }
    
    /// Requests notification permission (called when user accepts the prompt)
    @MainActor
    func requestNotificationPermission() async {
        // Mark that we've asked
        UserDefaults.standard.set(true, forKey: hasBeenAskedForNotificationsKey)
        
        // Request permission
        let granted = await NotificationManager.shared.requestNotificationPermission()
        
        if granted {
            print("✅ MatchesViewModel: Notification permission granted")
        } else {
            print("❌ MatchesViewModel: Notification permission denied")
        }
        
        // Hide the prompt
        showNotificationPrompt = false
    }
    
    /// Dismisses the notification prompt without requesting permission
    @MainActor
    func dismissNotificationPrompt() {
        // Mark that we've asked (so we don't ask again)
        UserDefaults.standard.set(true, forKey: hasBeenAskedForNotificationsKey)
        
        // Hide the prompt
        showNotificationPrompt = false
        
        print("ℹ️ MatchesViewModel: User dismissed notification prompt")
    }
    
    /// Says Yay to a match
    @MainActor
    func sayYay(to match: Match) async -> Bool {
        return await makeDecision(on: match, decision: true)
    }
    
    /// Says Nay to a match
    @MainActor
    func sayNay(to match: Match) async -> Bool {
        return await makeDecision(on: match, decision: false)
    }
    
    // MARK: - Full Refresh

    /// Performs a full refresh: creates new matches, then loads all matches
    @MainActor
    func refresh() async {
        await loadCurrentUser()
        await createPotentialMatches()
        await loadMatches()
    }

    /// Re-fetches matched user profiles from Firestore to pick up updated show-up meters
    /// and other user fields without reloading the full match list.
    /// Does NOT re-fetch Simpatico scores — questionnaire data doesn't change on show-up events.
    @MainActor
    func refreshMatchedUsers() async {
        await loadMatchedUserProfiles()
    }

    /// Returns the cached Simpatico compatibility score for the given mutual match, or nil
    /// if neither user has completed the questionnaire yet.
    func getSimpatico(for match: Match) -> Int? {
        guard let currentUserID = authService.currentUserID,
              let otherUserID = match.otherUserID(for: currentUserID) else { return nil }
        return simpaticoScores[otherUserID]
    }
    
    // MARK: - Helper Methods
    
    /// Gets the user object for a match
    func getUser(for match: Match) -> FirebaseUser? {
        guard let currentUserID = authService.currentUserID else { return nil }
        guard let otherUserID = match.otherUserID(for: currentUserID) else { return nil }
        return matchedUsers[otherUserID]
    }
    
    /// Gets the distance to a matched user in miles
    func getDistance(to match: Match) -> Double? {
        guard let currentUser = currentUser,
              let otherUser = getUser(for: match) else {
            return nil
        }
        
        let location1 = CLLocation(latitude: currentUser.latitude, longitude: currentUser.longitude)
        let location2 = CLLocation(latitude: otherUser.latitude, longitude: otherUser.longitude)
        let distanceInMeters = location1.distance(from: location2)
        #if DEBUG
        print("[Distance] me=(\(currentUser.latitude),\(currentUser.longitude)) them=(\(otherUser.latitude),\(otherUser.longitude)) uid=\(otherUser.id) result=\(distanceInMeters / 1609.34)")
        #endif
        return distanceInMeters / 1609.34 // Convert to miles
    }
    
    /// Clears error message
    func clearError() {
        errorMessage = nil
    }
}

// MARK: - Supporting Types

/// Combines a Match with the other User's profile for easier display
struct MatchWithUser: Identifiable, Equatable {
    let match: Match
    let otherUser: User
    let currentUser: FirebaseUser?
    let isOtherUserSharingLocation: Bool

    init(match: Match, otherUser: User, currentUser: FirebaseUser? = nil, isOtherUserSharingLocation: Bool = false) {
        self.match = match
        self.otherUser = otherUser
        self.currentUser = currentUser
        self.isOtherUserSharingLocation = isOtherUserSharingLocation
    }

    var id: String {
        match.id
    }

    /// Whether this is a mutual match
    var isMutual: Bool {
        match.isMutualMatch
    }

    /// Shared activities
    var sharedActivities: [String] {
        match.overlappingActivityNames
    }

    /// Shared time slots
    var sharedTimes: [String] {
        match.overlappingDaySlots
    }

    /// "You both like X" label for matches with no identical shared activity, where the
    /// only thing connecting them is an overlapping activity category (see
    /// MatchingService.createMatch). Nil when there's an exact shared activity to show instead.
    var categoryMatchLabel: String? {
        guard let categoryName = match.overlappingCategoryNames.first else { return nil }
        return "You both like \(categoryName)"
    }

    /// Bucketed distance label (e.g. "~5 mi away"), or nil if either party has
    /// no location on file, or the other user isn't sharing their location.
    var distanceText: String? {
        guard let currentUser,
              currentUser.latitude != 0 || currentUser.longitude != 0,
              otherUser.latitude != 0 || otherUser.longitude != 0
        else { return nil }

        let miles = CLLocation(latitude: currentUser.latitude, longitude: currentUser.longitude)
            .distance(from: CLLocation(latitude: otherUser.latitude, longitude: otherUser.longitude)) / 1609.34

        return DistanceDisplay.label(miles: miles, isSharing: isOtherUserSharingLocation)
    }

    static func == (lhs: MatchWithUser, rhs: MatchWithUser) -> Bool {
        lhs.match.id == rhs.match.id
    }
}
