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

    /// Whether this is one of the user's 3 "Main" activities (vs. an "Extra").
    /// Defaults to true so locally-cached rows from before this field existed
    /// migrate in as Main rather than silently losing weight in matching.
    var isPrimary: Bool = true

    init(id: String = UUID().uuidString, name: String, isUserAdded: Bool = false, createdAt: Date = Date(), isPrimary: Bool = true) {
        self.id = id
        self.name = name
        self.isUserAdded = isUserAdded
        self.createdAt = createdAt
        self.isPrimary = isPrimary
    }
}

/// Codable struct version of Activity (for Firebase/network operations)
struct Activity: Identifiable, Codable, Hashable {
    let id: String
    let name: String
    let isUserAdded: Bool
    let createdAt: Date

    /// Whether this is one of the user's 3 "Main" activities (vs. an "Extra").
    /// Missing/absent on read (pre-existing profiles) decodes as true, so everyone's
    /// current 3 activities become their Main ones automatically — no migration needed.
    var isPrimary: Bool

    init(
        id: String = UUID().uuidString,
        name: String,
        isUserAdded: Bool = false,
        createdAt: Date = Date(),
        isPrimary: Bool = true
    ) {
        self.id = id
        self.name = name
        self.isUserAdded = isUserAdded
        self.createdAt = createdAt
        self.isPrimary = isPrimary
    }

    /// Convert from SwiftData model to Codable struct
    init(from model: ActivityModel) {
        self.id = model.id
        self.name = model.name
        self.isUserAdded = model.isUserAdded
        self.createdAt = model.createdAt
        self.isPrimary = model.isPrimary
    }

    /// Normalizes a typed activity name for duplicate/overlap comparison:
    /// trims leading/trailing whitespace, collapses internal runs of whitespace
    /// to a single space, and lowercases. Display casing (e.g. "BJJ") is never
    /// altered — this is only used to decide if two names refer to the same activity.
    static func normalizedForComparison(_ name: String) -> String {
        name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .lowercased()
    }
}

extension ActivityModel {
    /// Convert from Codable struct to SwiftData model
    convenience init(from activity: Activity) {
        self.init(
            id: activity.id,
            name: activity.name,
            isUserAdded: activity.isUserAdded,
            createdAt: activity.createdAt,
            isPrimary: activity.isPrimary
        )
    }
}
