//
//  MatchingService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import CoreLocation

/// Protocol to disambiguate which User type we're using for matching
/// Only the Codable FirebaseUser struct from UserModel.swift conforms to this
protocol MatchingUser: Identifiable, Codable where ID == String {
    var displayName: String { get set }
    var activities: [Activity] { get set }
    var daySlotCombos: [DaySlotCombo] { get set }
    var latitude: Double { get set }
    var longitude: Double { get set }
    var radiusMiles: Double { get set }
}

/// Extend the Codable FirebaseUser struct to conform to MatchingUser
extension FirebaseUser: MatchingUser {}

/// Type alias for clarity
typealias AppUser = any MatchingUser

/// Service for matching logic between users
/// Determines if two users are compatible based on activities and availability
final class MatchingService {
    
    // MARK: - Singleton
    
    static let shared = MatchingService()
    
    private init() {}
    
    // MARK: - Core Matching Algorithm
    
    /// Determines if two users should be matched
    /// A match occurs when users share at least ONE activity (exact match OR shared
    /// activity category) AND at least ONE time slot
    func shouldMatch(user1: AppUser, user2: AppUser) -> Bool {
        let activitiesCompatible = hasOverlappingActivities(user1: user1, user2: user2) ||
            !sharedActivityCategories(user1: user1, user2: user2).isEmpty
        return activitiesCompatible && hasOverlappingTimes(user1: user1, user2: user2)
    }
    
    /// Checks if two users have at least one overlapping activity.
    /// Matches by ID first; falls back to a normalized-name comparison (trimmed,
    /// whitespace-collapsed, case-insensitive) so pre-existing duplicate entries
    /// like "Hiking" / "hiking " still count as the same activity.
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: True if at least one activity overlaps
    func hasOverlappingActivities(user1: AppUser, user2: AppUser) -> Bool {
        let user1ActivityIDs = Set(user1.activities.map { $0.id })
        let user2ActivityIDs = Set(user2.activities.map { $0.id })

        if !user1ActivityIDs.intersection(user2ActivityIDs).isEmpty {
            return true
        }

        let user1Names = Set(user1.activities.map { Activity.normalizedForComparison($0.name) })
        let user2Names = Set(user2.activities.map { Activity.normalizedForComparison($0.name) })
        return !user1Names.intersection(user2Names).isEmpty
    }
    
    /// Checks if two users have at least one overlapping day/time slot
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: True if at least one time slot overlaps
    func hasOverlappingTimes(user1: AppUser, user2: AppUser) -> Bool {
        let user1Combos = Set(user1.daySlotCombos)
        let user2Combos = Set(user2.daySlotCombos)
        
        return !user1Combos.intersection(user2Combos).isEmpty
    }
    
    // MARK: - Detailed Overlap Information
    
    /// Gets all overlapping activities between two users. Matches by ID or by
    /// normalized name (see `hasOverlappingActivities`).
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: Array of overlapping activities
    func getOverlappingActivities(user1: AppUser, user2: AppUser) -> [Activity] {
        let user1ActivityIDs = Set(user1.activities.map { $0.id })
        let user1Names = Set(user1.activities.map { Activity.normalizedForComparison($0.name) })

        return user2.activities.filter { activity in
            user1ActivityIDs.contains(activity.id) ||
            user1Names.contains(Activity.normalizedForComparison(activity.name))
        }
    }
    
    /// Gets all overlapping day/time slots between two users
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: Array of overlapping day/slot combinations
    func getOverlappingTimes(user1: AppUser, user2: AppUser) -> [DaySlotCombo] {
        let user1Combos = Set(user1.daySlotCombos)

        return user2.daySlotCombos.filter { combo in
            user1Combos.contains(combo)
        }
    }

    /// Categories represented by a user's activities. Custom/free-typed activities that
    /// aren't in ActivitiesDatabase.allActivities return nil from the lookup and are dropped.
    private func activityCategories(for user: AppUser) -> Set<ActivityCategory> {
        Set(user.activities.compactMap { ActivityCategories.category(for: $0.name) })
    }

    /// Categories shared between two users' activities, independent of exact activity overlap.
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: Array of shared categories
    func sharedActivityCategories(user1: AppUser, user2: AppUser) -> [ActivityCategory] {
        Array(activityCategories(for: user1).intersection(activityCategories(for: user2)))
    }

    /// Creates a match record between two users with overlap information
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: A Match object if users should be matched, nil otherwise
    func createMatch(between user1: AppUser, and user2: AppUser) -> Match? {
        let matched = shouldMatch(user1: user1, user2: user2)

        #if DEBUG
        let user2ActivityIDs = Set(user2.activities.map { $0.id })
        let user2Names = Set(user2.activities.map { Activity.normalizedForComparison($0.name) })
        let sharedFromUser1 = user1.activities.filter { activity in
            user2ActivityIDs.contains(activity.id) ||
            user2Names.contains(Activity.normalizedForComparison(activity.name))
        }
        let sharedMain = sharedFromUser1.filter { $0.isPrimary }.count
        let sharedExtra = sharedFromUser1.filter { !$0.isPrimary }.count
        let sharedSlots = Set(user1.daySlotCombos).intersection(Set(user2.daySlotCombos)).count
        print("[Match] me=\(user1.id) them=\(user2.id) sharedMain=\(sharedMain) sharedExtra=\(sharedExtra) sharedSlots=\(sharedSlots) result=\(matched ? "matched" : "not")")
        #endif

        // Check if users should be matched
        guard matched else {
            return nil
        }

        // Get overlapping activities
        let overlappingActivities = getOverlappingActivities(user1: user1, user2: user2)
        let activityNames = overlappingActivities.map { $0.name }

        // Shared categories only matter for display when there's no identical activity
        // already explaining the match — otherwise the exact-activity chips say enough.
        let categoryNames = activityNames.isEmpty
            ? sharedActivityCategories(user1: user1, user2: user2).map { $0.displayName }
            : []

        // Get overlapping times
        let overlappingTimes = getOverlappingTimes(user1: user1, user2: user2)
        let timeSlotStrings = overlappingTimes.map { $0.displayName }

        // Deterministic ID — same scheme used by claimTodayPlan — ensures concurrent
        // calls from both sides of the pair resolve to one document, not two.
        let u1 = min(user1.id, user2.id)
        let u2 = max(user1.id, user2.id)
        return Match(
            id: "\(u1)_\(u2)",
            user1ID: u1,
            user2ID: u2,
            overlappingActivityNames: activityNames,
            overlappingDaySlots: timeSlotStrings,
            overlappingCategoryNames: categoryNames
        )
    }
    
    // MARK: - Match Scoring (Optional Enhancement)
    
    /// Calculates a match score between two users (0.0 to 1.0)
    /// Higher scores indicate more compatibility
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: A score from 0.0 (no match) to 1.0 (perfect match)
    func calculateMatchScore(user1: AppUser, user2: AppUser) -> Double {
        // Check if they should match at all
        guard shouldMatch(user1: user1, user2: user2) else {
            return 0.0
        }
        
        // Calculate activity overlap percentage, scaled to the actual number of
        // activities each user has (not a fixed 3) — the max possible overlap is
        // bounded by whichever user picked fewer.
        let overlappingActivities = getOverlappingActivities(user1: user1, user2: user2)
        let maxPossibleActivities = max(1, min(user1.activities.count, user2.activities.count))
        let activityScore = Double(overlappingActivities.count) / Double(maxPossibleActivities)

        // Calculate time overlap percentage, scaled the same way.
        let overlappingTimes = getOverlappingTimes(user1: user1, user2: user2)
        let maxPossibleTimes = max(1, min(user1.daySlotCombos.count, user2.daySlotCombos.count))
        let timeScore = Double(overlappingTimes.count) / Double(maxPossibleTimes)
        
        // Average the two scores
        let averageScore = (activityScore + timeScore) / 2.0
        
        return min(averageScore, 1.0) // Ensure it doesn't exceed 1.0
    }
    
    // MARK: - Location-Based Matching
    
    /// Checks if two users are within each other's preferred radius
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: True if users are within each other's radius
    func areWithinRadius(user1: AppUser, user2: AppUser) -> Bool {
        let location1 = CLLocation(latitude: user1.latitude, longitude: user1.longitude)
        let location2 = CLLocation(latitude: user2.latitude, longitude: user2.longitude)
        
        let distanceInMeters = location1.distance(from: location2)
        let distanceInMiles = distanceInMeters / 1609.34
        
        // Check if distance is within both users' preferred radius
        return distanceInMiles <= user1.radiusMiles && distanceInMiles <= user2.radiusMiles
    }
    
    /// Checks if two users should be matched based on all criteria
    /// Including activities, times, AND location
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: True if users meet all matching criteria
    func shouldMatchWithLocation(user1: AppUser, user2: AppUser) -> Bool {
        return shouldMatch(user1: user1, user2: user2) && areWithinRadius(user1: user1, user2: user2)
    }
    
    // MARK: - Batch Matching
    
    /// Finds all potential matches for a user from a list of candidates
    /// - Parameters:
    ///   - user: The user to find matches for
    ///   - candidates: Array of potential match candidates
    ///   - includeLocation: Whether to include location filtering (default: true)
    /// - Returns: Array of users that match with the given user
    func findMatches(for user: AppUser, in candidates: [AppUser], includeLocation: Bool = true) -> [AppUser] {
        return candidates.filter { candidate in
            // Don't match with self
            guard candidate.id != user.id else { return false }
            
            // Check basic matching criteria
            let basicMatch = shouldMatch(user1: user, user2: candidate)
            
            // If location filtering is enabled, also check radius
            if includeLocation {
                return basicMatch && areWithinRadius(user1: user, user2: candidate)
            }
            
            return basicMatch
        }
    }
    
    /// Creates match records for all compatible users
    /// - Parameters:
    ///   - user: The user to find matches for
    ///   - candidates: Array of potential match candidates
    ///   - includeLocation: Whether to include location filtering (default: true)
    /// - Returns: Array of Match objects
    func createMatches(for user: AppUser, in candidates: [AppUser], includeLocation: Bool = true) -> [Match] {
        let matchedUsers = findMatches(for: user, in: candidates, includeLocation: includeLocation)
        
        return matchedUsers.compactMap { candidate in
            createMatch(between: user, and: candidate)
        }
    }
    
    // MARK: - Match Statistics
    
    /// Generates match statistics for a user and a potential match
    /// - Parameters:
    ///   - user1: The first user
    ///   - user2: The second user
    /// - Returns: MatchStatistics object with detailed information
    func getMatchStatistics(user1: AppUser, user2: AppUser) -> MatchStatistics {
        let overlappingActivities = getOverlappingActivities(user1: user1, user2: user2)
        let overlappingTimes = getOverlappingTimes(user1: user1, user2: user2)
        let matchScore = calculateMatchScore(user1: user1, user2: user2)
        let withinRadius = areWithinRadius(user1: user1, user2: user2)
        
        let location1 = CLLocation(latitude: user1.latitude, longitude: user1.longitude)
        let location2 = CLLocation(latitude: user2.latitude, longitude: user2.longitude)
        let distanceInMiles = location1.distance(from: location2) / 1609.34
        
        return MatchStatistics(
            overlappingActivities: overlappingActivities,
            overlappingTimes: overlappingTimes,
            matchScore: matchScore,
            distanceInMiles: distanceInMiles,
            withinRadius: withinRadius
        )
    }
}

// MARK: - Supporting Types

/// Statistics about a potential match between two users
struct MatchStatistics {
    /// Activities that both users share
    let overlappingActivities: [Activity]
    
    /// Day/time slots that both users share
    let overlappingTimes: [DaySlotCombo]
    
    /// Match score from 0.0 to 1.0
    let matchScore: Double
    
    /// Distance between users in miles
    let distanceInMiles: Double
    
    /// Whether users are within each other's preferred radius
    let withinRadius: Bool
    
    /// Whether this is a valid match (has overlaps in both activities and times)
    var isValidMatch: Bool {
        return !overlappingActivities.isEmpty && !overlappingTimes.isEmpty
    }
    
    /// Number of overlapping activities
    var activityOverlapCount: Int {
        overlappingActivities.count
    }
    
    /// Number of overlapping time slots
    var timeOverlapCount: Int {
        overlappingTimes.count
    }
    
    /// Formatted distance string
    var distanceString: String {
        String(format: "%.1f miles away", distanceInMiles)
    }
    
    /// Match quality description
    var qualityDescription: String {
        if matchScore >= 0.8 {
            return "Excellent match"
        } else if matchScore >= 0.6 {
            return "Great match"
        } else if matchScore >= 0.4 {
            return "Good match"
        } else if matchScore > 0 {
            return "Potential match"
        } else {
            return "No match"
        }
    }
}
