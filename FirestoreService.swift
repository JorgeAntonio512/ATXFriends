//
//  FirestoreService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseFirestore
import CoreLocation

/// Service layer for Firestore database operations
/// Handles reading and writing user data, activities, and matches
final class FirestoreService {
    // MARK: - Properties
    
    private let db = Firestore.firestore()
    
    // Collection names
    private let usersCollection = "users"
    private let activitiesCollection = "activities"
    private let matchesCollection = "matches"
    private let groupsCollection = "groups"
    
    // MARK: - Singleton
    
    static let shared = FirestoreService()
    
    private init() {}
    
    // MARK: - User Operations
    
    /// Creates a new user document in Firestore
    /// - Parameter user: The FirebaseUser model to create
    /// - Throws: Firestore errors
    func createUser(_ user: FirebaseUser) async throws {
        let userData = userToFirestoreData(user)
        try await db.collection(usersCollection).document(user.id).setData(userData)
    }
    
    /// Updates an existing user document in Firestore
    /// - Parameter user: The FirebaseUser model to update
    /// - Throws: Firestore errors
    func updateUser(_ user: FirebaseUser) async throws {
        let userData = userToFirestoreData(user)
        try await db.collection(usersCollection).document(user.id).setData(userData, merge: true)
    }

    /// Updates only the location fields on a user document, leaving every other field
    /// untouched. Used by LocationRepairService to backfill a real coordinate without
    /// risking a stale overwrite of the rest of the profile.
    /// - Throws: Firestore errors
    func updateUserLocation(userID: String, latitude: Double, longitude: Double) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "latitude": latitude,
            "longitude": longitude,
            "location": GeoPoint(latitude: latitude, longitude: longitude)
        ])
    }

    /// Updates the FCM token for a user
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - token: The FCM token to store
    /// - Throws: Firestore errors
    func updateFCMToken(userID: String, token: String) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "fcmToken": token,
            "fcmTokenUpdatedAt": Timestamp(date: Date())
        ])
    }
    
    /// Removes the FCM token for a user
    /// - Parameter userID: The user's Firebase UID
    /// - Throws: Firestore errors
    func removeFCMToken(userID: String) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "fcmToken": FieldValue.delete(),
            "fcmTokenUpdatedAt": FieldValue.delete()
        ])
    }

    /// Resets the unread notification count for a user
    /// - Parameter userID: The user's Firebase UID
    /// - Throws: Firestore errors
    func resetUnreadCount(userID: String) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "unreadCount": 0
        ])
    }
    
    // ─── ADD THESE THREE FUNCTIONS to FirestoreService.swift ─────────────────────
    // Place them after the existing resetUnreadCount function (around line 82)

        /// Decrements the unread notification count for a user by a specific amount
        func decrementUnreadCount(userID: String, by amount: Int) async throws {
            guard amount > 0 else { return }
            try await db.collection(usersCollection).document(userID).updateData([
                "unreadCount": FieldValue.increment(Int64(-amount))
            ])
        }

        /// Gets the current unread count for a user
        func getUnreadCount(userID: String) async throws -> Int {
            let doc = try await db.collection(usersCollection).document(userID).getDocument()
            return (doc.data()?["unreadCount"] as? Int) ?? 0
        }

        /// Marks a plan as viewed in Firestore
        func markPlanViewed(planID: String) async throws {
            try await db.collection("plans").document(planID).updateData([
                "isViewed": true
            ])
        }
    
    /// Updates notification preferences for a user
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - preferences: The notification preferences to update
    /// - Throws: Firestore errors
    func updateNotificationPreferences(userID: String, preferences: NotificationPreferences) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "notificationPreferences": [
                "newMatches": preferences.newMatches,
                "newMessages": preferences.newMessages,
                "planRequests": preferences.planRequests,
                "planConfirmations": preferences.planConfirmations,
                "groupUpdates": preferences.groupUpdates
            ],
            "updatedAt": Timestamp(date: Date())
        ])
    }
    
    /// Fetches a user document from Firestore by ID
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: A FirebaseUser model if found, nil otherwise
    /// - Throws: Firestore errors
    func fetchUser(userID: String) async throws -> FirebaseUser? {
        let document = try await db.collection(usersCollection).document(userID).getDocument()
        
        guard document.exists, let data = document.data() else {
            return nil
        }
        
        return firestoreDataToUser(id: userID, data: data)
    }
    
    /// Deletes a user document from Firestore
    /// - Parameter userID: The user's Firebase UID
    /// - Throws: Firestore errors
    func deleteUser(userID: String) async throws {
        try await db.collection(usersCollection).document(userID).delete()
    }
    
    /// Fetches nearby users within a specified radius
    /// - Parameters:
    ///   - center: The center location (current user's location)
    ///   - radiusMiles: The search radius in miles
    ///   - excludeUserID: User ID to exclude from results (typically the current user)
    /// - Returns: Array of nearby users
    /// - Throws: Firestore errors
    func fetchNearbyUsers(center: CLLocationCoordinate2D, radiusMiles: Double, excludeUserID: String) async throws -> [FirebaseUser] {
        // Note: For production, consider using GeoFirestore for efficient geoqueries
        // For now, we'll fetch all users and filter client-side (not scalable for large datasets)
        
        let snapshot = try await db.collection(usersCollection)
            .whereField("isProfileComplete", isEqualTo: true)
            .getDocuments()
        
        var nearbyUsers: [FirebaseUser] = []
        
        for document in snapshot.documents {
            let userID = document.documentID
            
            // Skip the excluded user
            guard userID != excludeUserID else { continue }
            
            if let user = firestoreDataToUser(id: userID, data: document.data()) {
                // Calculate distance
                let userLocation = CLLocation(latitude: user.latitude, longitude: user.longitude)
                let centerLocation = CLLocation(latitude: center.latitude, longitude: center.longitude)
                let distanceInMeters = centerLocation.distance(from: userLocation)
                let distanceInMiles = distanceInMeters / 1609.34

                #if DEBUG
                print("[Distance] me=(\(center.latitude),\(center.longitude)) them=(\(user.latitude),\(user.longitude)) uid=\(userID) result=\(distanceInMiles)")
                #endif

                // Check if within radius
                if distanceInMiles <= radiusMiles {
                    nearbyUsers.append(user)
                }
            }
        }
        
        return nearbyUsers
    }
    
    // MARK: - Event Operations
    
    /// Fetches a single event by ID
    /// - Parameter eventID: The event's ID
    /// - Returns: The event if found, nil otherwise
    /// - Throws: Firestore errors
    func fetchEvent(eventID: String) async throws -> Event? {
        let document = try await db.collection("events").document(eventID).getDocument()
        
        guard document.exists, let data = document.data() else {
            return nil
        }
        
        return firestoreDataToEvent(id: eventID, data: data)
    }
    
    /// Converts Firestore data to an Event model
    private func firestoreDataToEvent(id: String, data: [String: Any]) -> Event? {
        guard
            let name = data["name"] as? String,
            let heroImageURL = data["heroImageURL"] as? String,
            let weekendsArray = data["weekends"] as? [[String: Any]]
        else {
            return nil
        }
        
        var weekends: [EventWeekend] = []
        
        for (index, weekendData) in weekendsArray.enumerated() {
            guard
                let label = weekendData["label"] as? String,
                let startTimestamp = weekendData["startDate"] as? Timestamp,
                let endTimestamp = weekendData["endDate"] as? Timestamp
            else {
                continue
            }
            
            let weekend = EventWeekend(
                weekendNumber: index + 1,
                label: label,
                startDate: startTimestamp.dateValue(),
                endDate: endTimestamp.dateValue()
            )
            weekends.append(weekend)
        }
        
        return Event(id: id, name: name, heroImageURL: heroImageURL, weekends: weekends)
    }
    
    // MARK: - Activity Operations
    
    /// Fetches all activities from Firestore
    /// - Returns: Array of all activities
    /// - Throws: Firestore errors
    func fetchActivities() async throws -> [Activity] {
        let snapshot = try await db.collection(activitiesCollection)
            .order(by: "name")
            .getDocuments()
        
        var activities: [Activity] = []
        
        for document in snapshot.documents {
            if let activity = firestoreDataToActivity(id: document.documentID, data: document.data()) {
                activities.append(activity)
            }
        }
        
        return activities
    }
    
    /// Adds a new activity to Firestore
    /// - Parameters:
    ///   - activity: The Activity model to add
    ///   - category: The category the user picked for a new custom activity. Pass nil
    ///     when seeding the predefined catalog. When present, the doc is also flagged
    ///     `needsReview: true` for manual review — additive fields, no rules change.
    /// - Throws: Firestore errors
    func addActivity(_ activity: Activity, category: ActivityCategory? = nil) async throws {
        var activityData: [String: Any] = [
            "name": activity.name,
            "isUserAdded": activity.isUserAdded,
            "createdAt": Timestamp(date: activity.createdAt)
        ]

        if let category {
            activityData["category"] = category.rawValue
            activityData["needsReview"] = true
        }

        try await db.collection(activitiesCollection).document(activity.id).setData(activityData)
    }
    
    /// Seeds the activities collection with predefined activities from ActivitiesDatabase
    /// Only adds activities that don't already exist
    /// - Returns: Number of activities added
    /// - Throws: Firestore errors
    func seedActivitiesIfNeeded() async throws -> Int {
        // Check if activities already exist
        let snapshot = try await db.collection(activitiesCollection).limit(to: 1).getDocuments()
        
        // If there are already activities, skip seeding
        guard snapshot.documents.isEmpty else {
            return 0
        }
        
        // Seed with predefined activities
        var addedCount = 0
        
        for activityName in ActivitiesDatabase.allActivities {
            let activity = Activity(
                id: UUID().uuidString,
                name: activityName,
                isUserAdded: false,
                createdAt: Date()
            )
            
            try await addActivity(activity)
            addedCount += 1
        }
        
        return addedCount
    }
    
    /// Searches for activities by name (case-insensitive partial match)
    /// - Parameter searchText: The text to search for
    /// - Returns: Array of matching activities
    /// - Throws: Firestore errors
    func searchActivities(searchText: String) async throws -> [Activity] {
        // Firestore doesn't support case-insensitive search, so we fetch all and filter client-side
        let allActivities = try await fetchActivities()
        let lowercaseSearch = searchText.lowercased()
        
        return allActivities.filter { activity in
            activity.name.lowercased().contains(lowercaseSearch)
        }
    }
    
    // MARK: - Match Operations
    
    /// Creates a new match document in Firestore
    /// - Parameter match: The Match model to create
    /// - Throws: Firestore errors
    func createMatch(_ match: Match) async throws {
        // Query by user pair so this catches matches at any document ID — both the
        // deterministic "<u1>_<u2>" scheme used for new matches and legacy UUID docs.
        let existing = try await db.collection(matchesCollection)
            .whereField("user1ID", isEqualTo: match.user1ID)
            .whereField("user2ID", isEqualTo: match.user2ID)
            .limit(to: 1)
            .getDocuments()
        guard existing.documents.isEmpty else { return }
        let matchData = matchToFirestoreData(match)
        try await db.collection(matchesCollection).document(match.id).setData(matchData)
    }
    
    /// Updates an existing match document in Firestore
    /// Only updates decision-related fields to comply with security rules
    /// - Parameter match: The Match model to update
    /// - Throws: Firestore errors
    func updateMatch(_ match: Match) async throws {
        // Only update the specific fields that users are allowed to modify
        // This prevents permission errors from strict security rules
        var updateData: [String: Any] = [
            "isMutualMatch": match.isMutualMatch,
            "updatedAt": Timestamp(date: match.updatedAt)
        ]
        
        // Include decision fields (using 'as Any' to handle optional Bool)
        if let user1Decision = match.user1Decision {
            updateData["user1Decision"] = user1Decision
        }
        
        if let user2Decision = match.user2Decision {
            updateData["user2Decision"] = user2Decision
        }
        
        try await db.collection(matchesCollection)
            .document(match.id)
            .updateData(updateData)
    }
    
    /// Fetches all matches for a specific user
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: Array of matches involving this user
    /// - Throws: Firestore errors
    func fetchMatches(for userID: String) async throws -> [Match] {
        // Query for matches where user is either user1 or user2
        let snapshot1 = try await db.collection(matchesCollection)
            .whereField("user1ID", isEqualTo: userID)
            .getDocuments()
        
        let snapshot2 = try await db.collection(matchesCollection)
            .whereField("user2ID", isEqualTo: userID)
            .getDocuments()
        
        var matches: [Match] = []
        
        for document in snapshot1.documents {
            if let match = firestoreDataToMatch(id: document.documentID, data: document.data()) {
                matches.append(match)
            }
        }
        
        for document in snapshot2.documents {
            if let match = firestoreDataToMatch(id: document.documentID, data: document.data()) {
                matches.append(match)
            }
        }
        
        return matches
    }
    
    /// Fetches a single match by ID
    /// - Parameter matchID: The match's ID
    /// - Returns: The match if found, nil otherwise
    /// - Throws: Firestore errors
    func fetchMatch(matchID: String) async throws -> Match? {
        let document = try await db.collection(matchesCollection).document(matchID).getDocument()
        
        guard document.exists, let data = document.data() else {
            return nil
        }
        
        return firestoreDataToMatch(id: matchID, data: data)
    }
    
    // MARK: - Data Conversion Helpers
    
    /// Converts a FirebaseUser model to Firestore data
    private func userToFirestoreData(_ user: FirebaseUser) -> [String: Any] {
        return [
            "displayName": user.displayName,
            "bio": user.bio,
            "photoURLs": user.photoURLs,
            "activityIDs": user.activities.map { $0.id },
            "activityNames": user.activities.map { $0.name },
            "activityIsPrimary": user.activities.map { $0.isPrimary },
            "daySlotCombos": user.daySlotCombos.map { combo in
                return "\(combo.dayOfWeek.rawValue)_\(combo.timeSlot.rawValue)"
            },
            "location": GeoPoint(latitude: user.latitude, longitude: user.longitude),
            "latitude": user.latitude,
            "longitude": user.longitude,
            "radiusMiles": user.radiusMiles,
            "createdAt": Timestamp(date: user.createdAt),
            "updatedAt": Timestamp(date: user.updatedAt),
            "isProfileComplete": user.isProfileComplete,
            "notificationPreferences": [
                "newMatches": user.notificationPreferences.newMatches,
                "newMessages": user.notificationPreferences.newMessages,
                "planRequests": user.notificationPreferences.planRequests,
                "planConfirmations": user.notificationPreferences.planConfirmations,
                "groupUpdates": user.notificationPreferences.groupUpdates
            ],
            "blockedUsers": user.blockedUsers
        ]
    }
    
    /// Converts Firestore data to a FirebaseUser model
    private func firestoreDataToUser(id: String, data: [String: Any]) -> FirebaseUser? {
        guard
            let displayName = data["displayName"] as? String,
            let photoURLs = data["photoURLs"] as? [String],
            let latitude = data["latitude"] as? Double,
            let longitude = data["longitude"] as? Double,
            let radiusMiles = data["radiusMiles"] as? Double,
            let createdAtTimestamp = data["createdAt"] as? Timestamp,
            let updatedAtTimestamp = data["updatedAt"] as? Timestamp,
            let isProfileComplete = data["isProfileComplete"] as? Bool
        else {
            return nil
        }
        
        // Reconstruct activities from stored data. A missing/mismatched
        // "activityIsPrimary" array (pre-existing profiles) defaults every
        // activity to isPrimary=true — no migration needed.
        var activities: [Activity] = []
        if let activityIDs = data["activityIDs"] as? [String],
           let activityNames = data["activityNames"] as? [String],
           activityIDs.count == activityNames.count {
            let isPrimaryFlags = data["activityIsPrimary"] as? [Bool]
            let hasValidPrimaryFlags = isPrimaryFlags?.count == activityIDs.count
            for (index, id) in activityIDs.enumerated() {
                let activity = Activity(
                    id: id,
                    name: activityNames[index],
                    isUserAdded: false, // We don't store this info, assume false
                    createdAt: Date(),
                    isPrimary: hasValidPrimaryFlags ? isPrimaryFlags![index] : true
                )
                activities.append(activity)
            }
        }
        
        // Reconstruct day/slot combos from stored strings
        var daySlotCombos: [DaySlotCombo] = []
        if let comboStrings = data["daySlotCombos"] as? [String] {
            for comboString in comboStrings {
                // Format is "Monday_Morning"
                let parts = comboString.split(separator: "_")
                if parts.count == 2,
                   let day = DayOfWeek(rawValue: String(parts[0])),
                   let slot = TimeSlot(rawValue: String(parts[1])) {
                    daySlotCombos.append(DaySlotCombo(dayOfWeek: day, timeSlot: slot))
                }
            }
        }
        
        // Parse notification preferences (default to all enabled for backward compatibility)
        var notificationPreferences = NotificationPreferences()
        if let prefsData = data["notificationPreferences"] as? [String: Bool] {
            notificationPreferences = NotificationPreferences(
                newMatches: prefsData["newMatches"] ?? true,
                newMessages: prefsData["newMessages"] ?? true,
                planRequests: prefsData["planRequests"] ?? true,
                planConfirmations: prefsData["planConfirmations"] ?? true,
                groupUpdates: prefsData["groupUpdates"] ?? true
            )
        }
        
        // Parse blockedUsers array (default to empty array for backward compatibility)
        let blockedUsers = data["blockedUsers"] as? [String] ?? []
        
        // Parse bio (default to empty string for backward compatibility)
        let bio = data["bio"] as? String ?? ""

        // Parse show-up meter counters (default to 0; updated atomically via FieldValue.increment)
        let showUpThumbsUp = data["showUpThumbsUp"] as? Int ?? 0
        let showUpTotal = data["showUpTotal"] as? Int ?? 0
        
        return FirebaseUser(
            id: id,
            displayName: displayName,
            photoURLs: photoURLs,
            activities: activities,
            daySlotCombos: daySlotCombos,
            latitude: latitude,
            longitude: longitude,
            radiusMiles: radiusMiles,
            createdAt: createdAtTimestamp.dateValue(),
            updatedAt: updatedAtTimestamp.dateValue(),
            isProfileComplete: isProfileComplete,
            notificationPreferences: notificationPreferences,
            blockedUsers: blockedUsers,
            bio: bio,
            showUpThumbsUp: showUpThumbsUp,
            showUpTotal: showUpTotal
        )
    }
    
    /// Converts Firestore data to an Activity model
    private func firestoreDataToActivity(id: String, data: [String: Any]) -> Activity? {
        guard
            let name = data["name"] as? String,
            let isUserAdded = data["isUserAdded"] as? Bool,
            let createdAtTimestamp = data["createdAt"] as? Timestamp
        else {
            return nil
        }
        
        return Activity(
            id: id,
            name: name,
            isUserAdded: isUserAdded,
            createdAt: createdAtTimestamp.dateValue()
        )
    }
    
    /// Converts a Match model to Firestore data
    private func matchToFirestoreData(_ match: Match) -> [String: Any] {
        var data: [String: Any] = [
            "user1ID": match.user1ID,
            "user2ID": match.user2ID,
            "isMutualMatch": match.isMutualMatch,
            "createdAt": Timestamp(date: match.createdAt),
            "updatedAt": Timestamp(date: match.updatedAt),
            "overlappingActivityNames": match.overlappingActivityNames,
            "overlappingDaySlots": match.overlappingDaySlots,
            "overlappingCategoryNames": match.overlappingCategoryNames
        ]
        
        if let user1Decision = match.user1Decision {
            data["user1Decision"] = user1Decision
        }
        
        if let user2Decision = match.user2Decision {
            data["user2Decision"] = user2Decision
        }
        
        return data
    }
    
    /// Converts Firestore data to a Match model
    private func firestoreDataToMatch(id: String, data: [String: Any]) -> Match? {
        guard
            let user1ID = data["user1ID"] as? String,
            let user2ID = data["user2ID"] as? String,
            let isMutualMatch = data["isMutualMatch"] as? Bool,
            let createdAtTimestamp = data["createdAt"] as? Timestamp,
            let updatedAtTimestamp = data["updatedAt"] as? Timestamp,
            let overlappingActivityNames = data["overlappingActivityNames"] as? [String],
            let overlappingDaySlots = data["overlappingDaySlots"] as? [String]
        else {
            return nil
        }
        
        let user1Decision = data["user1Decision"] as? Bool
        let user2Decision = data["user2Decision"] as? Bool
        // Absent for matches created before this field existed.
        let overlappingCategoryNames = data["overlappingCategoryNames"] as? [String] ?? []

        return Match(
            id: id,
            user1ID: user1ID,
            user2ID: user2ID,
            user1Decision: user1Decision,
            user2Decision: user2Decision,
            isMutualMatch: isMutualMatch,
            createdAt: createdAtTimestamp.dateValue(),
            updatedAt: updatedAtTimestamp.dateValue(),
            overlappingActivityNames: overlappingActivityNames,
            overlappingDaySlots: overlappingDaySlots,
            overlappingCategoryNames: overlappingCategoryNames
        )
    }
    
    // MARK: - Group Operations
    
    /// Creates a new group in Firestore
    /// - Parameter group: The Group model to create
    /// - Returns: The group ID
    /// - Throws: Firestore errors
    func createGroup(_ group: Group) async throws -> String {
        let groupData = groupToFirestoreData(group)
        try await db.collection(groupsCollection).document(group.id).setData(groupData)
        
        // Add organizer as first member
        let organizer = GroupMember(
            userID: group.organizerID,
            role: .organizer,
            joinedAt: group.createdAt,
            confirmedPlan: false,
            joinMessage: nil
        )
        let memberData = groupMemberToFirestoreData(organizer)
        try await db.collection(groupsCollection)
            .document(group.id)
            .collection("members")
            .document(group.organizerID)
            .setData(memberData)
        
        return group.id
    }
    
    /// Fetches public groups within a user's distance settings using geoHash prefix matching
    /// - Parameter geoHashPrefix: The first 4 characters of the user's geoHash (≈40km radius)
    /// - Returns: Array of nearby groups
    /// - Throws: Firestore errors
    func fetchNearbyGroups(geoHashPrefix: String) async throws -> [Group] {
        // TEMPORARY: Removed geoHash filtering for development
        // TODO: Reinstate geoHash filtering before production launch once user geoHash storage is implemented
        print("🔍 Fetching groups with status == 'open'...")
        
        let snapshot = try await db.collection(groupsCollection)
            .whereField("status", isEqualTo: "open")
            .getDocuments()
        
        print("📊 Found \(snapshot.documents.count) open groups")
        
        var nearbyGroups: [Group] = []
        
        for document in snapshot.documents {
            if let group = firestoreDataToGroup(id: document.documentID, data: document.data()) {
                nearbyGroups.append(group)
                print("✅ Loaded group: \(group.activityName) at \(group.location)")
            } else {
                print("⚠️ Failed to parse group document: \(document.documentID)")
            }
        }
        
        print("🎯 Returning \(nearbyGroups.count) groups")
        return nearbyGroups
    }
    
    /// Fetches groups the current user is a member of
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: Array of groups the user is a member of
    /// - Throws: Firestore errors
    func fetchMyGroups(userID: String) async throws -> [Group] {
        // Get all groups where the user is a member
        let allGroupsSnapshot = try await db.collection(groupsCollection).getDocuments()
        
        var myGroups: [Group] = []
        
        for groupDoc in allGroupsSnapshot.documents {
            // Check if user is a member of this group
            let memberDoc = try await db.collection(groupsCollection)
                .document(groupDoc.documentID)
                .collection("members")
                .document(userID)
                .getDocument()
            
            if memberDoc.exists,
               let group = firestoreDataToGroup(id: groupDoc.documentID, data: groupDoc.data()) {
                myGroups.append(group)
            }
        }
        
        return myGroups
    }
    
    /// Fetches members of a group
    /// - Parameter groupID: The group's ID
    /// - Returns: Array of group members
    /// - Throws: Firestore errors
    func fetchGroupMembers(groupID: String) async throws -> [GroupMember] {
        let snapshot = try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .getDocuments()
        
        var members: [GroupMember] = []
        
        for document in snapshot.documents {
            if let member = firestoreDataToGroupMember(data: document.data()) {
                members.append(member)
            }
        }
        
        return members
    }
    
    /// Fetches a specific user's membership role in a group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's Firebase UID
    /// - Returns: The user's role if they are a member, nil otherwise
    func fetchGroupMemberRole(groupID: String, userID: String) async -> GroupMember.GroupMemberRole? {
        do {
            let document = try await db.collection(groupsCollection)
                .document(groupID)
                .collection("members")
                .document(userID)
                .getDocument()
            
            guard document.exists,
                  let data = document.data(),
                  let roleString = data["role"] as? String,
                  let role = GroupMember.GroupMemberRole(rawValue: roleString) else {
                return nil
            }
            
            return role
        } catch {
            print("❌ Error fetching member role: \(error)")
            return nil
        }
    }
    
    /// Fetches a single group by ID
    /// - Parameter groupID: The group's ID
    /// - Returns: The group if found, nil otherwise
    /// - Throws: Firestore errors
    func fetchGroup(groupID: String) async throws -> Group? {
        let document = try await db.collection(groupsCollection).document(groupID).getDocument()
        
        guard document.exists, let data = document.data() else {
            return nil
        }
        
        return firestoreDataToGroup(id: groupID, data: data)
    }
    
    /// Fetches a user's display name and first photo URL
    /// - Parameter userID: The user's Firebase UID
    /// - Returns: Tuple containing display name and optional photo URL
    func fetchUserPreview(userID: String) async -> (displayName: String, photoURL: String?) {
        do {
            let document = try await db.collection(usersCollection).document(userID).getDocument()
            
            guard document.exists, let data = document.data() else {
                return ("Unknown", nil)
            }
            
            let displayName = data["displayName"] as? String ?? "Unknown"
            let photoURLs = data["photoURLs"] as? [String]
            let photoURL = photoURLs?.first
            
            return (displayName, photoURL)
        } catch {
            print("❌ Error fetching user preview: \(error)")
            return ("Unknown", nil)
        }
    }
    
    // MARK: - Group Data Conversion Helpers
    
    /// Converts a Group model to Firestore data
    private func groupToFirestoreData(_ group: Group) -> [String: Any] {
        var data: [String: Any] = [
            "id": group.id,
            "activityName": group.activityName,
            "location": group.location,
            "dateTime": Timestamp(date: group.dateTime),
            "recurrence": group.recurrence.rawValue,
            "description": group.description,
            "minParticipants": group.minParticipants,
            "maxParticipants": group.maxParticipants,
            "privacy": group.privacy.rawValue,
            "status": group.status.rawValue,
            "organizerID": group.organizerID,
            "createdAt": Timestamp(date: group.createdAt),
            "geoHash": group.geoHash
        ]
        
        if let coverPhotoURL = group.coverPhotoURL {
            data["coverPhotoURL"] = coverPhotoURL
        }
        
        if let confirmedAt = group.confirmedAt {
            data["confirmedAt"] = Timestamp(date: confirmedAt)
        }
        
        if let confirmationDeadline = group.confirmationDeadline {
            data["confirmationDeadline"] = Timestamp(date: confirmationDeadline)
        }
        
        return data
    }
    
    /// Converts Firestore data to a Group model
    private func firestoreDataToGroup(id: String, data: [String: Any]) -> Group? {
        guard
            let activityName = data["activityName"] as? String,
            let location = data["location"] as? String,
            let dateTimeTimestamp = data["dateTime"] as? Timestamp,
            let recurrenceString = data["recurrence"] as? String,
            let recurrence = Group.GroupRecurrence(rawValue: recurrenceString),
            let description = data["description"] as? String,
            let minParticipants = data["minParticipants"] as? Int,
            let maxParticipants = data["maxParticipants"] as? Int,
            let privacyString = data["privacy"] as? String,
            let privacy = Group.GroupPrivacy(rawValue: privacyString),
            let statusString = data["status"] as? String,
            let status = Group.GroupStatus(rawValue: statusString),
            let organizerID = data["organizerID"] as? String,
            let createdAtTimestamp = data["createdAt"] as? Timestamp,
            let geoHash = data["geoHash"] as? String
        else {
            return nil
        }
        
        let coverPhotoURL = data["coverPhotoURL"] as? String
        let confirmedAt = (data["confirmedAt"] as? Timestamp)?.dateValue()
        let confirmationDeadline = (data["confirmationDeadline"] as? Timestamp)?.dateValue()
        
        return Group(
            id: id,
            activityName: activityName,
            location: location,
            dateTime: dateTimeTimestamp.dateValue(),
            recurrence: recurrence,
            description: description,
            coverPhotoURL: coverPhotoURL,
            minParticipants: minParticipants,
            maxParticipants: maxParticipants,
            privacy: privacy,
            status: status,
            organizerID: organizerID,
            createdAt: createdAtTimestamp.dateValue(),
            confirmedAt: confirmedAt,
            confirmationDeadline: confirmationDeadline,
            geoHash: geoHash
        )
    }
    
    /// Converts a GroupMember model to Firestore data
    private func groupMemberToFirestoreData(_ member: GroupMember) -> [String: Any] {
        var data: [String: Any] = [
            "userID": member.userID,
            "role": member.role.rawValue,
            "joinedAt": Timestamp(date: member.joinedAt),
            "confirmedPlan": member.confirmedPlan
        ]
        
        if let joinMessage = member.joinMessage {
            data["joinMessage"] = joinMessage
        }
        
        return data
    }
    
    /// Converts Firestore data to a GroupMember model
    private func firestoreDataToGroupMember(data: [String: Any]) -> GroupMember? {
        guard
            let userID = data["userID"] as? String,
            let roleString = data["role"] as? String,
            let role = GroupMember.GroupMemberRole(rawValue: roleString),
            let joinedAtTimestamp = data["joinedAt"] as? Timestamp,
            let confirmedPlan = data["confirmedPlan"] as? Bool
        else {
            return nil
        }
        
        let joinMessage = data["joinMessage"] as? String
        
        return GroupMember(
            userID: userID,
            role: role,
            joinedAt: joinedAtTimestamp.dateValue(),
            confirmedPlan: confirmedPlan,
            joinMessage: joinMessage
        )
    }
    
    // MARK: - Group Member Management
    
    /// Adds or updates a member in a group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID
    ///   - memberData: Dictionary containing member data
    /// - Throws: Firestore errors
    func setGroupMember(groupID: String, userID: String, memberData: [String: Any]) async throws {
        try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .document(userID)
            .setData(memberData)
    }
    
    /// Updates a group member's role
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID
    ///   - role: The new role as a string
    /// - Throws: Firestore errors
    func updateGroupMemberRole(groupID: String, userID: String, role: String) async throws {
        try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .document(userID)
            .updateData(["role": role])
    }
    
    /// Removes a member from a group
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - userID: The user's ID to remove
    /// - Throws: Firestore errors
    func removeGroupMember(groupID: String, userID: String) async throws {
        try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .document(userID)
            .delete()
    }
    
    /// Fetches waitlisted members ordered by join date
    /// - Parameter groupID: The group's ID
    /// - Returns: Query snapshot of waitlisted members
    /// - Throws: Firestore errors
    func fetchWaitlistedMembers(groupID: String) async throws -> QuerySnapshot {
        return try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .whereField("role", isEqualTo: "waitlisted")
            .order(by: "joinedAt", descending: false)
            .getDocuments()
    }
    
    /// Updates a group's status
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - status: The new status string
    /// - Throws: Firestore errors
    func updateGroupStatus(groupID: String, status: String) async throws {
        try await db.collection(groupsCollection)
            .document(groupID)
            .updateData(["status": status])
    }
    
    /// Updates a group's organizer ID
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - organizerID: The new organizer's user ID
    /// - Throws: Firestore errors
    func updateGroupOrganizer(groupID: String, organizerID: String) async throws {
        try await db.collection(groupsCollection)
            .document(groupID)
            .updateData(["organizerID": organizerID])
    }
    
    /// Performs a batch update for transferring organizer role
    /// - Parameters:
    ///   - groupID: The group's ID
    ///   - currentOrganizerID: Current organizer's user ID
    ///   - newOrganizerID: New organizer's user ID
    /// - Throws: Firestore errors
    func transferGroupOrganizer(groupID: String, currentOrganizerID: String, newOrganizerID: String) async throws {
        let batch = db.batch()
        
        // Current organizer becomes a regular member
        let currentOrgRef = db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .document(currentOrganizerID)
        batch.updateData(["role": "member"], forDocument: currentOrgRef)
        
        // New organizer
        let newOrgRef = db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .document(newOrganizerID)
        batch.updateData(["role": "organizer"], forDocument: newOrgRef)
        
        // Update organizerID on the group document
        let groupRef = db.collection(groupsCollection).document(groupID)
        batch.updateData(["organizerID": newOrganizerID], forDocument: groupRef)
        
        try await batch.commit()
    }
    
    /// Searches for users by display name prefix
    /// - Parameter prefix: The search prefix
    /// - Returns: Query snapshot of matching users
    /// - Throws: Firestore errors
    func searchUsersByDisplayName(prefix: String, limit: Int = 10) async throws -> QuerySnapshot {
        let endPrefix = prefix + "\u{f8ff}"
        return try await db.collection(usersCollection)
            .whereField("displayName", isGreaterThanOrEqualTo: prefix)
            .whereField("displayName", isLessThan: endPrefix)
            .limit(to: limit)
            .getDocuments()
    }
    
    /// Fetches all members of a group (returns document snapshot)
    /// - Parameter groupID: The group's ID
    /// - Returns: Query snapshot of group members
    /// - Throws: Firestore errors
    func fetchGroupMembersSnapshot(groupID: String) async throws -> QuerySnapshot {
        return try await db.collection(groupsCollection)
            .document(groupID)
            .collection("members")
            .getDocuments()
    }
}
