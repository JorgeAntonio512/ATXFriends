//
//  MatchingServiceExamples.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//
//  Example usage and test cases for the MatchingService

//import Foundation
//
///// Example usage and test scenarios for the matching algorithm
///// This file demonstrates how the matching service works
//struct MatchingServiceExamples {
//    
//    // MARK: - Sample Activities
//    
//    static func createSampleActivities() -> [Activity] {
//        return [
//            Activity(id: "1", name: "Hiking", isUserAdded: false),
//            Activity(id: "2", name: "Coffee", isUserAdded: false),
//            Activity(id: "3", name: "Board Games", isUserAdded: false),
//            Activity(id: "4", name: "Tacos", isUserAdded: false),
//            Activity(id: "5", name: "Yoga", isUserAdded: false),
//            Activity(id: "6", name: "Live Music", isUserAdded: false),
//            Activity(id: "7", name: "Dog Parks", isUserAdded: false),
//            Activity(id: "8", name: "Kayaking", isUserAdded: false)
//        ]
//    }
//    
//    // MARK: - Sample DaySlotCombos
//    
//    static func createSampleTimes() -> [DaySlotCombo] {
//        return [
//            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .evening),
//            DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//            DaySlotCombo(dayOfWeek: .friday, timeSlot: .night),
//            DaySlotCombo(dayOfWeek: .wednesday, timeSlot: .evening),
//            DaySlotCombo(dayOfWeek: .monday, timeSlot: .night)
//        ]
//    }
//    
//    // MARK: - Example Scenarios
//    
//    /// Example 1: Perfect Match
//    /// Alice and Bob share all 3 activities and all 3 times
//    static func examplePerfectMatch() {
//        let activities = createSampleActivities()
//        
//        // Both users have the same activities
//        let sharedActivities = Array(activities[0...2]) // Hiking, Coffee, Board Games
//        
//        // Both users have the same times
//        let sharedTimes = [
//            DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//            DaySlotCombo(dayOfWeek: .friday, timeSlot: .night),
//            DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon)
//        ]
//        
//        let alice = User(
//            id: "alice",
//            displayName: "Alice",
//            friendshipMode: .individual,
//            activities: sharedActivities,
//            daySlotCombos: sharedTimes,
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 10.0
//        )
//        
//        let bob = User(
//            id: "bob",
//            displayName: "Bob",
//            friendshipMode: .individual,
//            activities: sharedActivities,
//            daySlotCombos: sharedTimes,
//            latitude: 30.2700,
//            longitude: -97.7400,
//            radiusMiles: 10.0
//        )
//        
//        let matchingService = MatchingService.shared
//        
//        let shouldMatch = matchingService.shouldMatch(user1: alice, user2: bob)
//        let score = matchingService.calculateMatchScore(user1: alice, user2: bob)
//        let stats = matchingService.getMatchStatistics(user1: alice, user2: bob)
//        
//        print("=== Perfect Match Example ===")
//        print("Should match: \(shouldMatch)") // true
//        print("Match score: \(score)") // 1.0
//        print("Quality: \(stats.qualityDescription)") // Excellent match
//        print("Distance: \(stats.distanceString)")
//        print("Overlapping activities: \(stats.overlappingActivities.map { $0.name })")
//        print("Overlapping times: \(stats.overlappingTimes.map { $0.displayName })")
//        print()
//    }
//    
//    /// Example 2: Minimal Match
//    /// Charlie and Dana share exactly 1 activity and 1 time (minimum for a match)
//    static func exampleMinimalMatch() {
//        let activities = createSampleActivities()
//        
//        let charlie = User(
//            id: "charlie",
//            displayName: "Charlie",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//                DaySlotCombo(dayOfWeek: .monday, timeSlot: .evening),
//                DaySlotCombo(dayOfWeek: .wednesday, timeSlot: .afternoon)
//            ],
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 10.0
//        )
//        
//        let dana = User(
//            id: "dana",
//            displayName: "Dana",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[3], activities[4]], // Hiking (overlap!), Tacos, Yoga
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp), // Overlap!
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2700,
//            longitude: -97.7400,
//            radiusMiles: 10.0
//        )
//        
//        let matchingService = MatchingService.shared
//        
//        let shouldMatch = matchingService.shouldMatch(user1: charlie, user2: dana)
//        let score = matchingService.calculateMatchScore(user1: charlie, user2: dana)
//        let stats = matchingService.getMatchStatistics(user1: charlie, user2: dana)
//        
//        print("=== Minimal Match Example ===")
//        print("Should match: \(shouldMatch)") // true
//        print("Match score: \(score)") // 0.33 (1 of 3 activities, 1 of 3 times)
//        print("Quality: \(stats.qualityDescription)") // Potential match
//        print("Overlapping activities: \(stats.overlappingActivities.map { $0.name })") // [Hiking]
//        print("Overlapping times: \(stats.overlappingTimes.map { $0.displayName })") // [Saturday Wake Up]
//        print()
//    }
//    
//    /// Example 3: No Match - Activities Overlap but Times Don't
//    static func exampleNoMatchBecauseOfTimes() {
//        let activities = createSampleActivities()
//        
//        let evan = User(
//            id: "evan",
//            displayName: "Evan",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 10.0
//        )
//        
//        let fiona = User(
//            id: "fiona",
//            displayName: "Fiona",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Same activities!
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .monday, timeSlot: .evening), // Different times
//                DaySlotCombo(dayOfWeek: .wednesday, timeSlot: .night),
//                DaySlotCombo(dayOfWeek: .thursday, timeSlot: .afternoon)
//            ],
//            latitude: 30.2700,
//            longitude: -97.7400,
//            radiusMiles: 10.0
//        )
//        
//        let matchingService = MatchingService.shared
//        
//        let shouldMatch = matchingService.shouldMatch(user1: evan, user2: fiona)
//        let hasActivities = matchingService.hasOverlappingActivities(user1: evan, user2: fiona)
//        let hasTimes = matchingService.hasOverlappingTimes(user1: evan, user2: fiona)
//        
//        print("=== No Match (Times Don't Overlap) ===")
//        print("Should match: \(shouldMatch)") // false
//        print("Has overlapping activities: \(hasActivities)") // true
//        print("Has overlapping times: \(hasTimes)") // false
//        print("Result: No match because times don't align")
//        print()
//    }
//    
//    /// Example 4: No Match - Times Overlap but Activities Don't
//    static func exampleNoMatchBecauseOfActivities() {
//        let activities = createSampleActivities()
//        
//        let george = User(
//            id: "george",
//            displayName: "George",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 10.0
//        )
//        
//        let hannah = User(
//            id: "hannah",
//            displayName: "Hannah",
//            friendshipMode: .individual,
//            activities: [activities[3], activities[4], activities[5]], // Tacos, Yoga, Live Music (no overlap!)
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp), // Same times!
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2700,
//            longitude: -97.7400,
//            radiusMiles: 10.0
//        )
//        
//        let matchingService = MatchingService.shared
//        
//        let shouldMatch = matchingService.shouldMatch(user1: george, user2: hannah)
//        let hasActivities = matchingService.hasOverlappingActivities(user1: george, user2: hannah)
//        let hasTimes = matchingService.hasOverlappingTimes(user1: george, user2: hannah)
//        
//        print("=== No Match (Activities Don't Overlap) ===")
//        print("Should match: \(shouldMatch)") // false
//        print("Has overlapping activities: \(hasActivities)") // false
//        print("Has overlapping times: \(hasTimes)") // true
//        print("Result: No match because activities don't align")
//        print()
//    }
//    
//    /// Example 5: No Match - Outside Radius
//    static func exampleNoMatchBecauseOfLocation() {
//        let activities = createSampleActivities()
//        
//        let ivy = User(
//            id: "ivy",
//            displayName: "Ivy",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]],
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 5.0 // Small radius
//        )
//        
//        let jack = User(
//            id: "jack",
//            displayName: "Jack",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Same activities
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp), // Same times
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.3500, // Far away (~6 miles)
//            longitude: -97.8000,
//            radiusMiles: 5.0
//        )
//        
//        let matchingService = MatchingService.shared
//        
//        let shouldMatch = matchingService.shouldMatch(user1: ivy, user2: jack)
//        let withinRadius = matchingService.areWithinRadius(user1: ivy, user2: jack)
//        let shouldMatchWithLocation = matchingService.shouldMatchWithLocation(user1: ivy, user2: jack)
//        let stats = matchingService.getMatchStatistics(user1: ivy, user2: jack)
//        
//        print("=== No Match (Outside Radius) ===")
//        print("Should match (basic): \(shouldMatch)") // true
//        print("Within radius: \(withinRadius)") // false
//        print("Should match with location: \(shouldMatchWithLocation)") // false
//        print("Distance: \(stats.distanceString)")
//        print("Result: Match on activities/times, but too far apart")
//        print()
//    }
//    
//    /// Example 6: Batch Matching
//    static func exampleBatchMatching() {
//        let activities = createSampleActivities()
//        
//        let mainUser = User(
//            id: "main",
//            displayName: "Main User",
//            friendshipMode: .individual,
//            activities: [activities[0], activities[1], activities[2]], // Hiking, Coffee, Board Games
//            daySlotCombos: [
//                DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp),
//                DaySlotCombo(dayOfWeek: .sunday, timeSlot: .afternoon),
//                DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)
//            ],
//            latitude: 30.2672,
//            longitude: -97.7431,
//            radiusMiles: 10.0
//        )
//        
//        let candidates = [
//            // Match: shares Hiking and Saturday Wake Up
//            User(id: "c1", displayName: "Candidate 1", activities: [activities[0], activities[3], activities[4]],
//                 daySlotCombos: [DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp)],
//                 latitude: 30.2700, longitude: -97.7400, radiusMiles: 10.0),
//            
//            // No match: shares activities but not times
//            User(id: "c2", displayName: "Candidate 2", activities: [activities[0], activities[1]],
//                 daySlotCombos: [DaySlotCombo(dayOfWeek: .monday, timeSlot: .evening)],
//                 latitude: 30.2700, longitude: -97.7400, radiusMiles: 10.0),
//            
//            // Match: shares Coffee and Friday Night
//            User(id: "c3", displayName: "Candidate 3", activities: [activities[1], activities[5], activities[6]],
//                 daySlotCombos: [DaySlotCombo(dayOfWeek: .friday, timeSlot: .night)],
//                 latitude: 30.2700, longitude: -97.7400, radiusMiles: 10.0),
//            
//            // No match: too far away
//            User(id: "c4", displayName: "Candidate 4", activities: [activities[0], activities[1]],
//                 daySlotCombos: [DaySlotCombo(dayOfWeek: .saturday, timeSlot: .wakeUp)],
//                 latitude: 30.5000, longitude: -97.9000, radiusMiles: 5.0)
//        ]
//        
//        let matchingService = MatchingService.shared
//        
//        let matches = matchingService.findMatches(for: mainUser, in: candidates, includeLocation: true)
//        let matchRecords = matchingService.createMatches(for: mainUser, in: candidates, includeLocation: true)
//        
//        print("=== Batch Matching Example ===")
//        print("Found \(matches.count) matches out of \(candidates.count) candidates")
//        
//        for match in matchRecords {
//            print("\nMatch with: \(match.user2ID)")
//            print("  Activities: \(match.overlappingActivityNames)")
//            print("  Times: \(match.overlappingDaySlots)")
//        }
//        print()
//    }
//    
//    // MARK: - Run All Examples
//    
//    static func runAllExamples() {
//        print("\n🎯 AVENUE3 MATCHING SERVICE EXAMPLES\n")
//        
//        examplePerfectMatch()
//        exampleMinimalMatch()
//        exampleNoMatchBecauseOfTimes()
//        exampleNoMatchBecauseOfActivities()
//        exampleNoMatchBecauseOfLocation()
//        exampleBatchMatching()
//        
//        print("✅ All examples complete!\n")
//    }
//}
