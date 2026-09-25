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
    ///
    /// Coordinates are snapped to the coarse grid before writing — this field is
    /// readable by other users (see firestore.rules), so nothing more precise than
    /// ~0.7 mi may ever land here.
    /// - Throws: Firestore errors
    func updateUserLocation(userID: String, latitude: Double, longitude: Double) async throws {
        let snapped = CoarseLocation.snap(CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        try await db.collection(usersCollection).document(userID).updateData([
            "latitude": snapped.latitude,
            "longitude": snapped.longitude,
            "location": GeoPoint(latitude: snapped.latitude, longitude: snapped.longitude)
        ])
    }

    /// Updates the "Share My Location" mode and, when a fresh fix is provided, the
    /// coarse coordinate and `locationUpdatedAt`. Passing `coordinate: nil` (e.g. for
    /// a switch to .off) updates only the mode, leaving the last stored location and
    /// its timestamp untouched.
    /// - Throws: Firestore errors
    func updateLocationSharing(userID: String, mode: LocationSharingMode, coordinate: CLLocationCoordinate2D?) async throws {
        var data: [String: Any] = ["locationSharingMode": mode.rawValue]
        if let coordinate {
            let snapped = CoarseLocation.snap(coordinate)
            data["latitude"] = snapped.latitude
            data["longitude"] = snapped.longitude
            data["location"] = GeoPoint(latitude: snapped.latitude, longitude: snapped.longitude)
            data["locationUpdatedAt"] = Timestamp(date: Date())
        }
        try await db.collection(usersCollection).document(userID).updateData(data)
    }

    /// Adds this device's FCM token to the user's token list (multi-device safe —
    /// arrayUnion is a no-op if the token is already present).
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - token: The FCM token to store
    /// - Throws: Firestore errors
    func addFCMToken(userID: String, token: String) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "fcmTokens": FieldValue.arrayUnion([token]),
            "fcmTokenUpdatedAt": Timestamp(date: Date())
        ])
    }

    /// Removes exactly this device's FCM token from the user's token list on
    /// sign-out. Unlike overwriting a single field, this can never affect
    /// another device's (or another account's) token in the same array.
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - token: This device's FCM token
    /// - Throws: Firestore errors
    func removeFCMToken(userID: String, token: String) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "fcmTokens": FieldValue.arrayRemove([token])
        ])
    }

    /// One-time migration: folds the legacy single `fcmToken` field into the
    /// `fcmTokens` array, then deletes the legacy field. Safe to call every
    /// launch — it's a no-op once the legacy field is gone.
    /// - Parameter userID: The user's Firebase UID
    /// - Throws: Firestore errors
    func migrateLegacyFCMTokenIfNeeded(userID: String) async throws {
        let doc = try await db.collection(usersCollection).document(userID).getDocument()
        guard let legacyToken = doc.data()?["fcmToken"] as? String, !legacyToken.isEmpty else { return }
        try await db.collection(usersCollection).document(userID).updateData([
            "fcmTokens": FieldValue.arrayUnion([legacyToken]),
            "fcmToken": FieldValue.delete(),
            "fcmTokenUpdatedAt": FieldValue.delete()
        ])
    }

    /// Sets the unread notification count for a user to an exact value. The
    /// client (UnreadState) is the single source of truth for this number —
    /// unread messages plus plans needing attention, recomputed from scratch
    /// on every listener fire — and mirrors it here so the server has the
    /// right badge number to attach to a push while the app is closed.
    /// - Parameters:
    ///   - userID: The user's Firebase UID
    ///   - count: The exact badge count to store
    /// - Throws: Firestore errors
    func setUnreadCount(userID: String, count: Int) async throws {
        try await db.collection(usersCollection).document(userID).updateData([
            "unreadCount": count
        ])
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
        // Snapped here too, defense-in-depth: this is the same field
        // updateUserLocation/updateLocationSharing write to, and the one other
        // users' match cards read — only coarse coordinates may ever land in it.
        let snapped = CoarseLocation.snap(CLLocationCoordinate2D(latitude: user.latitude, longitude: user.longitude))
        var data: [String: Any] = [
            "displayName": user.displayName,
            "bio": user.bio,
            "photoURLs": user.photoURLs,
            "activityIDs": user.activities.map { $0.id },
            "activityNames": user.activities.map { $0.name },
            "activityIsPrimary": user.activities.map { $0.isPrimary },
            "daySlotCombos": user.daySlotCombos.map { combo in
                return "\(combo.dayOfWeek.rawValue)_\(combo.timeSlot.rawValue)"
            },
            "location": GeoPoint(latitude: snapped.latitude, longitude: snapped.longitude),
            "latitude": snapped.latitude,
            "longitude": snapped.longitude,
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
            "blockedUsers": user.blockedUsers,
            "locationSharingMode": user.locationSharingMode
        ]
        // Only set when present so a merge-write (updateUser) never clobbers an
        // already-stored timestamp with nil.
        if let locationUpdatedAt = user.locationUpdatedAt {
            data["locationUpdatedAt"] = Timestamp(date: locationUpdatedAt)
        }
        return data
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

        // Default to "off" for every account that predates this setting
        let locationSharingMode = data["locationSharingMode"] as? String ?? LocationSharingMode.off.rawValue
        let locationUpdatedAt = (data["locationUpdatedAt"] as? Timestamp)?.dateValue()

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
            showUpTotal: showUpTotal,
            locationSharingMode: locationSharingMode,
            locationUpdatedAt: locationUpdatedAt
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
}
