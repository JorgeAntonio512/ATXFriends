//
//  MatchingServiceTests.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import XCTest
@testable import Avenue3

/// Unit tests for the MatchingService algorithm
/// Tests the core matching logic that determines if two users should be matched
class MatchingServiceTests: XCTestCase {
    
    // MARK: - Test Data Setup
    
    /// Creates sample activities for testing
    func createTestActivities() -> [Activity] {
        return [
            Activity(id: "activity1", name: "Hiking", isUserAdded: false),
            Activity(id: "activity2", name: "Coffee", isUserAdded: false),
            Activity(id: "activity3", name: "Board Games", isUserAdded: false),
            Activity(id: "activity4", name: "Tacos", isUserAdded: false),
            Activity(id: "activity5", name: "Yoga", isUserAdded: false),
            Activity(id: "activity6", name: "Live Music", isUserAdded: false)
        ]
    }
    
    /// Creates sample day/slot combos for testing
    func createTestTimes() -> [DaySlotCombo] {
        return [
            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .evening),
            DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
            DaySlotCombo(dayOfWeek: .friday, timeSlot: .night),
            DaySlotCombo(dayOfWeek: .monday, timeSlot: .evening),
            DaySlotCombo(dayOfWeek: .wednesday, timeSlot: .night)
        ]
    }
    
    // MARK: - Test Case 1: Users Match (Activity + Time Overlap)
    
    /// Two users who share both activity and time should match
    func testUsersMatchWithOverlap() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // User 1: Hiking, Coffee, Board Games at Saturday Wake Up, Friday Night, Sunday Afternoon
        let user1 = User(
            id: "user1",
            displayName: "Alice",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
            daySlotCombos: [times[0], times[3], times[2]], // Saturday Wake Up, Friday Night, Sunday Afternoon
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // User 2: Hiking, Tacos, Yoga at Saturday Wake Up, Monday Evening, Wednesday Night
        let user2 = User(
            id: "user2",
            displayName: "Bob",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[3], activities[4]], // Hiking (OVERLAP!), Tacos, Yoga
            daySlotCombos: [times[0], times[4], times[5]], // Saturday Wake Up (OVERLAP!), Monday Evening, Wednesday Night
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // Test matching
        let shouldMatch = matchingService.shouldMatch(user1: user1, user2: user2)
        let hasActivities = matchingService.hasOverlappingActivities(user1: user1, user2: user2)
        let hasTimes = matchingService.hasOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertTrue(shouldMatch, "Users with overlapping activity and time should match")
        XCTAssertTrue(hasActivities, "Users should have overlapping activities")
        XCTAssertTrue(hasTimes, "Users should have overlapping times")
        
        // Verify overlap details
        let overlappingActivities = matchingService.getOverlappingActivities(user1: user1, user2: user2)
        let overlappingTimes = matchingService.getOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertEqual(overlappingActivities.count, 1, "Should have exactly 1 overlapping activity")
        XCTAssertEqual(overlappingActivities[0].name, "Hiking", "Overlapping activity should be Hiking")
        
        XCTAssertEqual(overlappingTimes.count, 1, "Should have exactly 1 overlapping time")
        XCTAssertEqual(overlappingTimes[0].dayOfWeek, .saturday, "Overlapping day should be Saturday")
        XCTAssertEqual(overlappingTimes[0].timeSlot, .wakeUp, "Overlapping time should be Wake Up")
    }
    
    // MARK: - Test Case 2: Activity Overlap but NO Time Overlap (Should NOT Match)
    
    /// Two users who share activity but NO time should NOT match
    func testUsersDoNotMatchWithoutTimeOverlap() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // User 1: Hiking, Coffee, Board Games at Saturday Wake Up, Friday Night, Sunday Afternoon
        let user1 = User(
            id: "user1",
            displayName: "Charlie",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
            daySlotCombos: [times[0], times[3], times[2]], // Saturday Wake Up, Friday Night, Sunday Afternoon
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // User 2: Same activities but DIFFERENT times
        let user2 = User(
            id: "user2",
            displayName: "Diana",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[1], activities[2]], // Same: Hiking, Coffee, Board Games
            daySlotCombos: [times[1], times[4], times[5]], // DIFFERENT: Saturday Evening, Monday Evening, Wednesday Night
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // Test matching
        let shouldMatch = matchingService.shouldMatch(user1: user1, user2: user2)
        let hasActivities = matchingService.hasOverlappingActivities(user1: user1, user2: user2)
        let hasTimes = matchingService.hasOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertFalse(shouldMatch, "Users without overlapping times should NOT match")
        XCTAssertTrue(hasActivities, "Users should have overlapping activities")
        XCTAssertFalse(hasTimes, "Users should NOT have overlapping times")
        
        // Verify overlap details
        let overlappingActivities = matchingService.getOverlappingActivities(user1: user1, user2: user2)
        let overlappingTimes = matchingService.getOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertEqual(overlappingActivities.count, 3, "Should have 3 overlapping activities")
        XCTAssertEqual(overlappingTimes.count, 0, "Should have NO overlapping times")
    }
    
    // MARK: - Test Case 3: Time Overlap but NO Activity Overlap (Should NOT Match)
    
    /// Two users who share time but NO activity should NOT match
    func testUsersDoNotMatchWithoutActivityOverlap() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // User 1: Hiking, Coffee, Board Games at Saturday Wake Up, Friday Night, Sunday Afternoon
        let user1 = User(
            id: "user1",
            displayName: "Evan",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
            daySlotCombos: [times[0], times[3], times[2]], // Saturday Wake Up, Friday Night, Sunday Afternoon
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // User 2: DIFFERENT activities but same times
        let user2 = User(
            id: "user2",
            displayName: "Fiona",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[3], activities[4], activities[5]], // DIFFERENT: Tacos, Yoga, Live Music
            daySlotCombos: [times[0], times[3], times[2]], // Same: Saturday Wake Up, Friday Night, Sunday Afternoon
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // Test matching
        let shouldMatch = matchingService.shouldMatch(user1: user1, user2: user2)
        let hasActivities = matchingService.hasOverlappingActivities(user1: user1, user2: user2)
        let hasTimes = matchingService.hasOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertFalse(shouldMatch, "Users without overlapping activities should NOT match")
        XCTAssertFalse(hasActivities, "Users should NOT have overlapping activities")
        XCTAssertTrue(hasTimes, "Users should have overlapping times")
        
        // Verify overlap details
        let overlappingActivities = matchingService.getOverlappingActivities(user1: user1, user2: user2)
        let overlappingTimes = matchingService.getOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertEqual(overlappingActivities.count, 0, "Should have NO overlapping activities")
        XCTAssertEqual(overlappingTimes.count, 3, "Should have 3 overlapping times")
    }
    
    // MARK: - Test Case 4: Zero Overlap (Should NOT Match)
    
    /// Two users with zero overlap should NOT match
    func testUsersDoNotMatchWithZeroOverlap() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // User 1: Hiking, Coffee, Board Games at Saturday Wake Up, Friday Night, Sunday Afternoon
        let user1 = User(
            id: "user1",
            displayName: "George",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
            daySlotCombos: [times[0], times[3], times[2]], // Saturday Wake Up, Friday Night, Sunday Afternoon
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // User 2: Completely different activities AND times
        let user2 = User(
            id: "user2",
            displayName: "Hannah",

            photoURLs: ["url1", "url2", "url3"],
            activities: [activities[3], activities[4], activities[5]], // DIFFERENT: Tacos, Yoga, Live Music
            daySlotCombos: [times[1], times[4], times[5]], // DIFFERENT: Saturday Evening, Monday Evening, Wednesday Night
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0,
            isProfileComplete: true
        )
        
        // Test matching
        let shouldMatch = matchingService.shouldMatch(user1: user1, user2: user2)
        let hasActivities = matchingService.hasOverlappingActivities(user1: user1, user2: user2)
        let hasTimes = matchingService.hasOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertFalse(shouldMatch, "Users with zero overlap should NOT match")
        XCTAssertFalse(hasActivities, "Users should NOT have overlapping activities")
        XCTAssertFalse(hasTimes, "Users should NOT have overlapping times")
        
        // Verify overlap details
        let overlappingActivities = matchingService.getOverlappingActivities(user1: user1, user2: user2)
        let overlappingTimes = matchingService.getOverlappingTimes(user1: user1, user2: user2)
        
        XCTAssertEqual(overlappingActivities.count, 0, "Should have NO overlapping activities")
        XCTAssertEqual(overlappingTimes.count, 0, "Should have NO overlapping times")
    }
    
    // MARK: - Additional Test Cases
    
    /// Match creation returns nil when users should not match
    func testMatchCreationReturnsNilForNonMatchingUsers() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // Users with no overlap
        let user1 = User(
            id: "user1",
            displayName: "User1",
            activities: [activities[0]],
            daySlotCombos: [times[0]],
            latitude: 30.2672,
            longitude: -97.7431
        )
        
        let user2 = User(
            id: "user2",
            displayName: "User2",
            activities: [activities[1]],
            daySlotCombos: [times[1]],
            latitude: 30.2700,
            longitude: -97.7400
        )
        
        let match = matchingService.createMatch(between: user1, and: user2)
        
        XCTAssertNil(match, "createMatch should return nil when users don't match")
    }
    
    /// Match creation returns valid Match object when users should match
    func testMatchCreationReturnsValidMatchForMatchingUsers() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // Users with overlap
        let user1 = User(
            id: "user1",
            displayName: "User1",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2672,
            longitude: -97.7431
        )
        
        let user2 = User(
            id: "user2",
            displayName: "User2",
            activities: [activities[0], activities[3], activities[4]], // Shares activities[0]
            daySlotCombos: [times[0], times[3], times[4]], // Shares times[0]
            latitude: 30.2700,
            longitude: -97.7400
        )
        
        let match = try XCTUnwrap(matchingService.createMatch(between: user1, and: user2))
        
        XCTAssertEqual(match.user1ID, "user1", "Match should have correct user1ID")
        XCTAssertEqual(match.user2ID, "user2", "Match should have correct user2ID")
        XCTAssertEqual(match.overlappingActivityNames.count, 1, "Match should have 1 overlapping activity")
        XCTAssertEqual(match.overlappingDaySlots.count, 1, "Match should have 1 overlapping time slot")
        XCTAssertFalse(match.isMutualMatch, "New match should not be mutual yet")
        XCTAssertNil(match.user1Decision, "User 1 decision should be nil initially")
        XCTAssertNil(match.user2Decision, "User 2 decision should be nil initially")
    }
    
    /// Match score calculation reflects overlap percentage
    func testMatchScoreCalculation() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // Scenario 1: Perfect match (3/3 activities, 3/3 times) = 1.0
        let perfectUser1 = User(
            id: "p1",
            displayName: "Perfect1",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2672,
            longitude: -97.7431
        )
        
        let perfectUser2 = User(
            id: "p2",
            displayName: "Perfect2",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2700,
            longitude: -97.7400
        )
        
        let perfectScore = matchingService.calculateMatchScore(user1: perfectUser1, user2: perfectUser2)
        XCTAssertEqual(perfectScore, 1.0, "Perfect overlap should have score of 1.0")
        
        // Scenario 2: Minimal match (1/3 activities, 1/3 times) ≈ 0.33
        let minimalUser1 = User(
            id: "m1",
            displayName: "Minimal1",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2672,
            longitude: -97.7431
        )
        
        let minimalUser2 = User(
            id: "m2",
            displayName: "Minimal2",
            activities: [activities[0], activities[3], activities[4]],
            daySlotCombos: [times[0], times[3], times[4]],
            latitude: 30.2700,
            longitude: -97.7400
        )
        
        let minimalScore = matchingService.calculateMatchScore(user1: minimalUser1, user2: minimalUser2)
        XCTAssertTrue(minimalScore > 0.3 && minimalScore < 0.4, "Minimal overlap should have score around 0.33")
        
        // Scenario 3: No match = 0.0
        let noMatchUser1 = User(
            id: "n1",
            displayName: "NoMatch1",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2672,
            longitude: -97.7431
        )
        
        let noMatchUser2 = User(
            id: "n2",
            displayName: "NoMatch2",
            activities: [activities[3], activities[4], activities[5]],
            daySlotCombos: [times[3], times[4], times[5]],
            latitude: 30.2700,
            longitude: -97.7400
        )
        
        let noMatchScore = matchingService.calculateMatchScore(user1: noMatchUser1, user2: noMatchUser2)
        XCTAssertEqual(noMatchScore, 0.0, "No overlap should have score of 0.0")
    }
    
    /// Location-based matching respects radius settings
    func testLocationBasedMatching() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        // Both users share activity and time
        let closeUser1 = User(
            id: "c1",
            displayName: "Close1",
            activities: [activities[0]],
            daySlotCombos: [times[0]],
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0
        )
        
        // User nearby (< 10 miles)
        let closeUser2 = User(
            id: "c2",
            displayName: "Close2",
            activities: [activities[0]],
            daySlotCombos: [times[0]],
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0
        )
        
        let areWithinRadius = matchingService.areWithinRadius(user1: closeUser1, user2: closeUser2)
        let shouldMatchWithLocation = matchingService.shouldMatchWithLocation(user1: closeUser1, user2: closeUser2)
        
        XCTAssertTrue(areWithinRadius, "Close users should be within radius")
        XCTAssertTrue(shouldMatchWithLocation, "Close users with overlap should match with location check")
        
        // User far away (> 10 miles) - using much smaller radius
        let farUser = User(
            id: "f1",
            displayName: "Far",
            activities: [activities[0]],
            daySlotCombos: [times[0]],
            latitude: 30.5000,
            longitude: -97.9000,
            radiusMiles: 1.0 // Small radius
        )
        
        let areFarWithinRadius = matchingService.areWithinRadius(user1: closeUser1, user2: farUser)
        let shouldMatchFarWithLocation = matchingService.shouldMatchWithLocation(user1: closeUser1, user2: farUser)
        
        XCTAssertFalse(areFarWithinRadius, "Far users should NOT be within radius")
        XCTAssertFalse(shouldMatchFarWithLocation, "Far users should NOT match with location check")
    }
    
    /// Match statistics provide accurate information
    func testMatchStatistics() async throws {
        let activities = createTestActivities()
        let times = createTestTimes()
        let matchingService = MatchingService.shared
        
        let user1 = User(
            id: "u1",
            displayName: "User1",
            activities: [activities[0], activities[1], activities[2]],
            daySlotCombos: [times[0], times[1], times[2]],
            latitude: 30.2672,
            longitude: -97.7431,
            radiusMiles: 10.0
        )
        
        let user2 = User(
            id: "u2",
            displayName: "User2",
            activities: [activities[0], activities[1], activities[3]], // 2 overlaps
            daySlotCombos: [times[0], times[3], times[4]], // 1 overlap
            latitude: 30.2700,
            longitude: -97.7400,
            radiusMiles: 10.0
        )
        
        let stats = matchingService.getMatchStatistics(user1: user1, user2: user2)
        
        XCTAssertTrue(stats.isValidMatch, "Statistics should indicate valid match")
        XCTAssertEqual(stats.activityOverlapCount, 2, "Should have 2 overlapping activities")
        XCTAssertEqual(stats.timeOverlapCount, 1, "Should have 1 overlapping time")
        XCTAssertTrue(stats.withinRadius, "Users should be within radius")
        XCTAssertGreaterThan(stats.matchScore, 0.0, "Match score should be greater than 0")
        XCTAssertGreaterThan(stats.distanceInMiles, 0.0, "Distance should be calculated")
        XCTAssertEqual(stats.overlappingActivities.count, 2, "Should have 2 activities in detail")
        XCTAssertEqual(stats.overlappingTimes.count, 1, "Should have 1 time in detail")
    }
}
