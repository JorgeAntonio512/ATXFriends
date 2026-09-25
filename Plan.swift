//
//  Plan.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/17/26.
//

import Foundation

/// Represents a proposed or confirmed hangout plan between matched users
struct Plan: Identifiable, Codable {
    /// Unique identifier
    let id: String
    
    /// ID of the match this plan belongs to
    let matchID: String
    
    /// User who created/proposed the plan
    let proposerID: String
    
    /// User who receives the proposal
    let receiverID: String
    
    /// Selected activity for the hangout
    let activity: Activity
    
    /// Optional location (free text) — display string used by the Apple/Google/Microsoft
    /// calendar integrations exactly as before. Never rename/repurpose this: those three
    /// integrations read it as-is.
    let location: String?

    /// Name of the place selected via location search (nil for free-typed or legacy locations).
    let locationName: String?

    /// Coordinates of the place selected via location search, for tap-to-navigate.
    /// Nil for plans created before this feature, or when the location was free-typed.
    let locationLatitude: Double?
    let locationLongitude: Double?

    /// Three proposed dates
    let proposedDates: [Date]
    
    /// Status of the plan
    var status: PlanStatus
    
    /// If status is confirmed, this is the selected date
    var confirmedDate: Date?
    
    /// Timestamp when the plan was created
    let createdAt: Date
    
    /// Timestamp when the plan was last updated
    var updatedAt: Date
    
    /// If the receiver counter-proposes, store their proposed dates here
    var counterProposedDates: [Date]?

    /// User who requested the pending reschedule (status == .counterProposed). Written
    /// together with the counter status and counterProposedDates; cleared on accept/decline.
    /// Nil on legacy counter plans created before this field existed.
    var counterProposedBy: String?
    
    init(
        id: String = UUID().uuidString,
        matchID: String,
        proposerID: String,
        receiverID: String,
        activity: Activity,
        location: String? = nil,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        proposedDates: [Date],
        status: PlanStatus = .pending,
        confirmedDate: Date? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        counterProposedDates: [Date]? = nil,
        counterProposedBy: String? = nil
    ) {
        self.id = id
        self.matchID = matchID
        self.proposerID = proposerID
        self.receiverID = receiverID
        self.activity = activity
        self.location = location
        self.locationName = locationName
        self.locationLatitude = locationLatitude
        self.locationLongitude = locationLongitude
        self.proposedDates = proposedDates
        self.status = status
        self.confirmedDate = confirmedDate
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.counterProposedDates = counterProposedDates
        self.counterProposedBy = counterProposedBy
    }
    
    /// Returns whether this user is the proposer
    func isProposer(userID: String) -> Bool {
        return proposerID == userID
    }
    
    /// Returns whether this user is the receiver
    func isReceiver(userID: String) -> Bool {
        return receiverID == userID
    }
    
    // MARK: - Reschedule

    /// True for plans that are on: confirmed, or confirmed with a reschedule request pending.
    /// While a request is pending the original confirmedDate still stands.
    var isConfirmedOrReschedulePending: Bool {
        status == .confirmed || status == .counterProposed
    }

    /// The suggested new time while a reschedule request is pending; nil otherwise.
    var pendingRescheduleDate: Date? {
        status == .counterProposed ? counterProposedDates?.first : nil
    }

    /// The time the plan currently stands at — confirmedDate, falling back to the
    /// original proposed date for legacy counter plans that never had one.
    var standingDate: Date? {
        confirmedDate ?? (status == .counterProposed ? proposedDates.first : nil)
    }

    /// Whether the plan still belongs in "upcoming" lists. A plan with a pending
    /// reschedule stays upcoming if either its standing time or the suggested time is ahead.
    func isUpcoming(now: Date = Date()) -> Bool {
        guard isConfirmedOrReschedulePending else { return false }
        if let date = standingDate, date > now { return true }
        if let date = pendingRescheduleDate, date > now { return true }
        return false
    }

    /// Whether this user may accept or decline the pending reschedule request.
    /// Only the non-requester can answer; legacy requests with no counterProposedBy
    /// can be answered by either participant so they can get unstuck.
    func canRespondToReschedule(userID: String) -> Bool {
        guard status == .counterProposed, userID == proposerID || userID == receiverID else { return false }
        guard let requester = counterProposedBy else { return true }
        return requester != userID
    }

    /// Returns the other user's ID
    func otherUserID(for userID: String) -> String {
        if userID == proposerID {
            return receiverID
        } else {
            return proposerID
        }
    }
}

/// Status of a plan
enum PlanStatus: String, Codable, CaseIterable {
    case pending = "pending"           // Waiting for receiver to respond
    case counterProposed = "counter"   // Receiver suggested different dates
    case confirmed = "confirmed"       // Date is locked in
    case declined = "declined"         // Receiver declined
    case cancelled = "cancelled"       // Either party cancelled
    
    var displayName: String {
        switch self {
        case .pending: "Pending Response"
        case .counterProposed: "Counter Proposal"
        case .confirmed: "Confirmed"
        case .declined: "Declined"
        case .cancelled: "Cancelled"
        }
    }
    
    var icon: String {
        switch self {
        case .pending: "clock.fill"
        case .counterProposed: "arrow.left.arrow.right"
        case .confirmed: "checkmark.circle.fill"
        case .declined: "xmark.circle.fill"
        case .cancelled: "xmark.circle"
        }
    }
}
