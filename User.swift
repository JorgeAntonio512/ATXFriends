//
//  User.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftData
import CoreLocation

/// Represents a user in the Avenue3 app
/// Stored locally with SwiftData and synced to Firestore
@Model
final class User {
    /// Unique identifier (matches Firebase Auth UID)
    @Attribute(.unique) var id: String
    
    /// User's display name
    var displayName: String
    
    /// Profile photo URLs (exactly 3)
    var photoURLs: [String]
    
    /// User's selected activities (exactly 3)
    /// Stored as relationships to Activity objects
    @Relationship(deleteRule: .nullify) var activities: [ActivityModel]
    
    /// User's preferred day/time combinations (exactly 3)
    /// Stored as inline value types
    var daySlotCombos: [DaySlotComboModel]
    
    /// User's location (latitude)
    var latitude: Double
    
    /// User's location (longitude)
    var longitude: Double
    
    /// User's preferred matching radius in miles (default 10)
    var radiusMiles: Double
    
    /// Timestamp when the user was created
    var createdAt: Date
    
    /// Timestamp when the user last updated their profile
    var updatedAt: Date
    
    /// Whether the user's profile is complete and ready for matching
    var isProfileComplete: Bool
    
    init(
        id: String,
        displayName: String = "",
        photoURLs: [String] = [],
        activities: [ActivityModel] = [],
        daySlotCombos: [DaySlotComboModel] = [],
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        radiusMiles: Double = 10.0,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        isProfileComplete: Bool = false
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
    }
    
    /// Returns the user's location as a CLLocationCoordinate2D
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    /// Validates whether the profile meets the 3-by-3 requirements
    func validateProfile() -> Bool {
        photoURLs.count == 3 &&
        activities.count == 3 &&
        daySlotCombos.count == 3 &&
        !displayName.isEmpty
    }
    
    /// Convert SwiftData User to FirebaseUser (for syncing to Firestore)
    func toFirebaseUser() -> FirebaseUser {
        FirebaseUser(
            id: self.id,
            displayName: self.displayName,
            photoURLs: self.photoURLs,
            activities: self.activities.map { Activity(from: $0) },
            daySlotCombos: self.daySlotCombos.map { DaySlotCombo(from: $0) },
            latitude: self.latitude,
            longitude: self.longitude,
            radiusMiles: self.radiusMiles,
            createdAt: self.createdAt,
            updatedAt: self.updatedAt,
            isProfileComplete: self.isProfileComplete
        )
    }
}
