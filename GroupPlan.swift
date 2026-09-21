//
//  GroupPlan.swift
//  Avenue3
//

import Foundation

/// An invitee's response to a group plan invite.
enum GroupPlanResponse: String, Codable {
    case invited
    case going
    case cantMake
}

/// Lifecycle status of a group plan.
enum GroupPlanStatus: String, Codable {
    case active
    case cancelled
}

/// A hangout plan proposed by one host to several matches at once. Distinct from `Plan`
/// (1-on-1 proposals): no counter-proposal flow, no messaging thread — the host picks a
/// single date up front and invitees independently say Going or Can't make it.
struct GroupPlan: Identifiable, Codable {
    let id: String

    /// User who created the invite.
    let hostID: String

    /// Every user invited to this plan.
    let inviteeIDs: [String]

    /// Each invitee's response, keyed by user ID. Every invitee starts as `.invited`.
    var responses: [String: GroupPlanResponse]

    /// Selected activity for the hangout.
    let activity: Activity

    /// Optional location (free text) — same meaning as on `Plan`.
    let location: String?

    /// Name of the place selected via location search (nil for free-typed locations).
    let locationName: String?

    /// Coordinates of the place selected via location search, for tap-to-navigate.
    let locationLatitude: Double?
    let locationLongitude: Double?

    /// The single date/time for this plan.
    let date: Date

    /// Status of the plan.
    var status: GroupPlanStatus

    let createdAt: Date
    var updatedAt: Date

    init(
        id: String = UUID().uuidString,
        hostID: String,
        inviteeIDs: [String],
        responses: [String: GroupPlanResponse]? = nil,
        activity: Activity,
        location: String? = nil,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        date: Date,
        status: GroupPlanStatus = .active,
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.hostID = hostID
        self.inviteeIDs = inviteeIDs
        self.responses = responses ?? Dictionary(uniqueKeysWithValues: inviteeIDs.map { ($0, .invited) })
        self.activity = activity
        self.location = location
        self.locationName = locationName
        self.locationLatitude = locationLatitude
        self.locationLongitude = locationLongitude
        self.date = date
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    func isHost(userID: String) -> Bool { hostID == userID }
    func isInvitee(userID: String) -> Bool { inviteeIDs.contains(userID) }
    func response(for userID: String) -> GroupPlanResponse? { responses[userID] }

    var goingCount: Int { responses.values.filter { $0 == .going }.count }
}
