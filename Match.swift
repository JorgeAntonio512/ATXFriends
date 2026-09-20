//
//  Match.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import Foundation
import SwiftData

/// SwiftData model for a match between two users
@Model
final class Match {
    @Attribute(.unique) var id: String
    var user1ID: String
    var user2ID: String
    var user1Decision: Bool?
    var user2Decision: Bool?
    var isMutualMatch: Bool
    var createdAt: Date
    var updatedAt: Date
    var overlappingActivityNames: [String]
    var overlappingDaySlots: [String]
    
    init(
        id: String = UUID().uuidString,
        user1ID: String,
        user2ID: String,
        user1Decision: Bool? = nil,
        user2Decision: Bool? = nil,
        isMutualMatch: Bool = false,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        overlappingActivityNames: [String] = [],
        overlappingDaySlots: [String] = []
    ) {
        self.id = id
        self.user1ID = user1ID
        self.user2ID = user2ID
        self.user1Decision = user1Decision
        self.user2Decision = user2Decision
        self.isMutualMatch = isMutualMatch
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.overlappingActivityNames = overlappingActivityNames
        self.overlappingDaySlots = overlappingDaySlots
    }
    
    /// Returns the other user's ID given the current user's ID
    func otherUserID(for currentUserID: String) -> String? {
        if user1ID == currentUserID {
            return user2ID
        } else if user2ID == currentUserID {
            return user1ID
        }
        return nil
    }
    
    /// Returns the decision for a specific user
    func decision(for userID: String) -> Bool? {
        if userID == user1ID {
            return user1Decision
        } else if userID == user2ID {
            return user2Decision
        }
        return nil
    }
    
    /// Sets the decision for a specific user
    func setDecision(for userID: String, decision: Bool) {
        if userID == user1ID {
            user1Decision = decision
        } else if userID == user2ID {
            user2Decision = decision
        }
        
        // Update mutual match status
        updateMutualMatchStatus()
        updatedAt = Date()
    }
    
    /// Updates the mutual match status based on both decisions
    private func updateMutualMatchStatus() {
        if let decision1 = user1Decision, let decision2 = user2Decision {
            isMutualMatch = decision1 && decision2
        } else {
            isMutualMatch = false
        }
    }
    
    /// Whether this match is still pending a decision from a specific user
    func isPending(for userID: String) -> Bool {
        return decision(for: userID) == nil
    }
    
    /// Whether either user said Nay (match rejected)
    var isRejected: Bool {
        return user1Decision == false || user2Decision == false
    }
}

// MARK: - Codable Match

/// Codable struct for Match (for Firebase operations)
struct MatchData: Identifiable, Codable {
    let id: String
    let user1ID: String
    let user2ID: String
    var user1Decision: Bool?
    var user2Decision: Bool?
    var isMutualMatch: Bool
    var createdAt: Date
    var updatedAt: Date
    var overlappingActivityNames: [String]
    var overlappingDaySlots: [String]
    
    init(
        id: String = UUID().uuidString,
        user1ID: String,
        user2ID: String,
        user1Decision: Bool? = nil,
        user2Decision: Bool? = nil,
        isMutualMatch: Bool = false,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        overlappingActivityNames: [String] = [],
        overlappingDaySlots: [String] = []
    ) {
        self.id = id
        self.user1ID = user1ID
        self.user2ID = user2ID
        self.user1Decision = user1Decision
        self.user2Decision = user2Decision
        self.isMutualMatch = isMutualMatch
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.overlappingActivityNames = overlappingActivityNames
        self.overlappingDaySlots = overlappingDaySlots
    }
    
    /// Returns the other user's ID given the current user's ID
    func otherUserID(for currentUserID: String) -> String? {
        if user1ID == currentUserID {
            return user2ID
        } else if user2ID == currentUserID {
            return user1ID
        }
        return nil
    }
    
    /// Convert from SwiftData model
    init(from model: Match) {
        self.id = model.id
        self.user1ID = model.user1ID
        self.user2ID = model.user2ID
        self.user1Decision = model.user1Decision
        self.user2Decision = model.user2Decision
        self.isMutualMatch = model.isMutualMatch
        self.createdAt = model.createdAt
        self.updatedAt = model.updatedAt
        self.overlappingActivityNames = model.overlappingActivityNames
        self.overlappingDaySlots = model.overlappingDaySlots
    }
}

extension Match {
    /// Convert from Codable struct
    convenience init(from matchData: MatchData) {
        self.init(
            id: matchData.id,
            user1ID: matchData.user1ID,
            user2ID: matchData.user2ID,
            user1Decision: matchData.user1Decision,
            user2Decision: matchData.user2Decision,
            isMutualMatch: matchData.isMutualMatch,
            createdAt: matchData.createdAt,
            updatedAt: matchData.updatedAt,
            overlappingActivityNames: matchData.overlappingActivityNames,
            overlappingDaySlots: matchData.overlappingDaySlots
        )
    }
}
