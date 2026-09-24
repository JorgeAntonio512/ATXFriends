//
//  UserModel.swift
//  Avenue3
//
//  User model for Avenue3 friend-finding app
//

import Foundation
import CoreLocation

/// Codable User struct for Firebase/network operations
/// This is separate from the SwiftData User model in User.swift
/// Represents a user for Firestore/network serialization
struct FirebaseUser: Identifiable, Codable {
    /// Unique identifier (Firebase UID)
    let id: String
    
    /// Display name
    var displayName: String
    
    /// Profile photo URLs (max 3)
    var photoURLs: [String]
    
    /// Selected activities (max 3)
    var activities: [Activity]
    
    /// Selected day/slot combinations (max 3)
    var daySlotCombos: [DaySlotCombo]
    
    /// User's latitude
    var latitude: Double
    
    /// User's longitude
    var longitude: Double
    
    /// Search radius in miles
    var radiusMiles: Double
    
    /// Account creation timestamp
    var createdAt: Date
    
    /// Last profile update timestamp
    var updatedAt: Date
    
    /// Whether the profile is complete (passed 3x3 validation)
    var isProfileComplete: Bool
    
    /// Notification preferences
    var notificationPreferences: NotificationPreferences
    
    /// Blocked user IDs
    var blockedUsers: [String]
    
    /// User's email address
    var email: String
    
    /// User's bio
    var bio: String
    
    /// Scheduled deletion date (if account deletion is scheduled)
    var scheduledDeletionDate: Date?
    
    /// Whether account is scheduled for deletion
    var isScheduledForDeletion: Bool

    /// "off" | "once" | "onOpen" — see LocationSharingMode. Defaults to "off" for
    /// every existing and new user.
    var locationSharingMode: String

    /// When a sharing mode last wrote a (coarse) location. Nil until the first write.
    var locationUpdatedAt: Date?

    // MARK: - Show-up Meter
    // Managed atomically via FieldValue.increment in TodayPlanService — never written by updateUser.

    /// Total thumbs-up responses this user has received from Today plan partners
    var showUpThumbsUp: Int
    /// Total show-up responses received about this user (thumbs-up + thumbs-down)
    var showUpTotal: Int

    // MARK: - Initializers
    
    init(
        id: String,
        displayName: String,
        photoURLs: [String] = [],
        activities: [Activity] = [],
        daySlotCombos: [DaySlotCombo] = [],
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        radiusMiles: Double = 10.0,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        isProfileComplete: Bool = false,
        notificationPreferences: NotificationPreferences = NotificationPreferences(),
        blockedUsers: [String] = [],
        email: String = "",
        bio: String = "",
        scheduledDeletionDate: Date? = nil,
        isScheduledForDeletion: Bool = false,
        showUpThumbsUp: Int = 0,
        showUpTotal: Int = 0,
        locationSharingMode: String = LocationSharingMode.off.rawValue,
        locationUpdatedAt: Date? = nil
    ) {
        self.id = id
        self.displayName = displayName
        self.photoURLs = photoURLs
        self.activities = activities
        self.daySlotCombos = daySlotCombos
        self.latitude = latitude
        self.longitude = longitude
        self.radiusMiles = radiusMiles
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.isProfileComplete = isProfileComplete
        self.notificationPreferences = notificationPreferences
        self.blockedUsers = blockedUsers
        self.email = email
        self.bio = bio
        self.scheduledDeletionDate = scheduledDeletionDate
        self.isScheduledForDeletion = isScheduledForDeletion
        self.showUpThumbsUp = showUpThumbsUp
        self.showUpTotal = showUpTotal
        self.locationSharingMode = locationSharingMode
        self.locationUpdatedAt = locationUpdatedAt
    }
    
    // MARK: - Computed Properties
    
    /// Returns the user's location as CLLocationCoordinate2D
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    /// Display string for the show-up reliability meter.
    /// Shows "New" when no responses yet; otherwise "75% (3/4)" so a small sample is transparent.
    var showUpMeter: String {
        guard showUpTotal > 0 else { return "New" }
        let pct = Int(Double(showUpThumbsUp) / Double(showUpTotal) * 100)
        return "\(pct)% (\(showUpThumbsUp)/\(showUpTotal))"
    }

    /// Validates that the profile meets requirements: exactly 3 photos,
    /// 3-10 activities with exactly 3 marked Main, and 3+ time slots (no max).
    var isValid: Bool {
        return photoURLs.count == 3 &&
               (3...10).contains(activities.count) &&
               activities.filter { $0.isPrimary }.count == 3 &&
               daySlotCombos.count >= 3 &&
               !displayName.isEmpty &&
               latitude != 0.0 &&
               longitude != 0.0
    }
}

// MARK: - Conversion Extension

extension FirebaseUser {
    /// Convert FirebaseUser to SwiftData User (for local storage)
    func toUser() -> User {
        User(
            id: self.id,
            displayName: self.displayName,
            photoURLs: self.photoURLs,
            activities: self.activities.map { ActivityModel(from: $0) },
            daySlotCombos: self.daySlotCombos.map { DaySlotComboModel(from: $0) },
            latitude: self.latitude,
            longitude: self.longitude,
            radiusMiles: self.radiusMiles,
            createdAt: self.createdAt,
            updatedAt: self.updatedAt,
            isProfileComplete: self.isProfileComplete
        )
    }
}

// MARK: - Notification Preferences

/// User's notification preferences
struct NotificationPreferences: Codable {
    var newMatches: Bool
    var newMessages: Bool
    var planRequests: Bool
    var planConfirmations: Bool
    var groupUpdates: Bool
    
    init(
        newMatches: Bool = true,
        newMessages: Bool = true,
        planRequests: Bool = true,
        planConfirmations: Bool = true,
        groupUpdates: Bool = true
    ) {
        self.newMatches = newMatches
        self.newMessages = newMessages
        self.planRequests = planRequests
        self.planConfirmations = planConfirmations
        self.groupUpdates = groupUpdates
    }
}
