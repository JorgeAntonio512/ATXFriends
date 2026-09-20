//
//  TodayPlan.swift
//  Avenue3
//

import Foundation

/// A spontaneous one-on-one activity post scoped to the current calendar day.
/// Any user can post an open plan; any other user can claim it first-come-first-served.
/// Once claimed, both participants get a chat thread (via the existing messaging layer)
/// and are each asked independently after the scheduled time whether the other showed up.
struct TodayPlan: Identifiable, Codable {
    let id: String
    let creatorID: String
    let activity: Activity
    /// Specific date and time the hangout is planned for (must fall on today's calendar date)
    let scheduledTime: Date
    /// Optional short note (e.g. "bring sunscreen") — kept separate from location.
    let note: String?
    /// Where the plan is happening — free text, required at creation via PlanLocationField.
    /// Optional here (not at the UI layer) so plans posted before this field existed still decode.
    let location: String?
    /// Name of the place selected via location search (nil for free-typed locations).
    let locationName: String?
    /// Coordinates of the place selected via location search, for tap-to-navigate.
    let locationLatitude: Double?
    let locationLongitude: Double?
    var status: TodayPlanStatus
    /// Set to the claiming user's ID the moment someone claims the plan
    var claimerID: String?
    let createdAt: Date
    var updatedAt: Date

    // MARK: - Show-up Reports
    // Each participant independently reports on the other after scheduledTime passes.
    // Mirrors Match.user1Decision / user2Decision — optional Bool, nil means not yet reported.
    // A nil never counts against either side.

    /// Creator's thumbs-up (true) or thumbs-down (false) about the claimer; nil until submitted
    var creatorReportedClaimer: Bool?
    /// Claimer's thumbs-up (true) or thumbs-down (false) about the creator; nil until submitted
    var claimerReportedCreator: Bool?

    init(
        id: String = UUID().uuidString,
        creatorID: String,
        activity: Activity,
        scheduledTime: Date,
        note: String? = nil,
        location: String? = nil,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        status: TodayPlanStatus = .open,
        claimerID: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        creatorReportedClaimer: Bool? = nil,
        claimerReportedCreator: Bool? = nil
    ) {
        self.id = id
        self.creatorID = creatorID
        self.activity = activity
        self.scheduledTime = scheduledTime
        self.note = note
        self.location = location
        self.locationName = locationName
        self.locationLatitude = locationLatitude
        self.locationLongitude = locationLongitude
        self.status = status
        self.claimerID = claimerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.creatorReportedClaimer = creatorReportedClaimer
        self.claimerReportedCreator = claimerReportedCreator
    }

    // MARK: - Derived State

    /// True when scheduledTime is in the past. No stored flag — derived on every read.
    var isExpired: Bool {
        scheduledTime < Date()
    }

    /// Returns the other participant's user ID (creator ↔ claimer).
    func otherUserID(for userID: String) -> String? {
        if userID == creatorID { return claimerID }
        if userID == claimerID { return creatorID }
        return nil
    }

    /// True when this user still needs to submit a show-up report about the other person.
    func awaitingReport(from userID: String) -> Bool {
        guard isExpired, status == .claimed, claimerID != nil else { return false }
        if userID == creatorID { return creatorReportedClaimer == nil }
        if userID == claimerID { return claimerReportedCreator == nil }
        return false
    }

    /// The thumbs-up/down this user submitted about the other person, or nil if not yet reported.
    func report(givenBy userID: String) -> Bool? {
        if userID == creatorID { return creatorReportedClaimer }
        if userID == claimerID { return claimerReportedCreator }
        return nil
    }
}

// MARK: - Status

enum TodayPlanStatus: String, Codable, CaseIterable {
    case open = "open"
    case claimed = "claimed"

    var displayName: String {
        switch self {
        case .open:    "Open"
        case .claimed: "Claimed"
        }
    }
}
