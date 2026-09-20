//
//  Activity.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftData

/// SwiftData model version of Activity (for local persistence)
/// Activities are stored both locally (SwiftData) and in Firestore as a shared global list
@Model
final class ActivityModel {
    /// Unique identifier (matches Firestore document ID)
    @Attribute(.unique) var id: String
    
    /// The name of the activity (e.g. "Hiking", "Board Games", "Tacos")
    var name: String
    
    /// Whether this was added by a user (vs. being part of the initial fixed list)
    var isUserAdded: Bool
    
    /// Timestamp when this activity was added
    var createdAt: Date
    
    init(id: String = UUID().uuidString, name: String, isUserAdded: Bool = false, createdAt: Date = Date()) {
        self.id = id
        self.name = name
        self.isUserAdded = isUserAdded
        self.createdAt = createdAt
    }
}

/// Codable struct version of Activity (for Firebase/network operations)
struct Activity: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let isUserAdded: Bool
    let createdAt: Date
    
    init(
        id: String = UUID().uuidString,
        name: String,
        isUserAdded: Bool = false,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.isUserAdded = isUserAdded
        self.createdAt = createdAt
    }
    
    /// Convert from SwiftData model to Codable struct
    init(from model: ActivityModel) {
        self.id = model.id
        self.name = model.name
        self.isUserAdded = model.isUserAdded
        self.createdAt = model.createdAt
    }
}

extension ActivityModel {
    /// Convert from Codable struct to SwiftData model
    convenience init(from activity: Activity) {
        self.init(
            id: activity.id,
            name: activity.name,
            isUserAdded: activity.isUserAdded,
            createdAt: activity.createdAt
        )
    }
}
