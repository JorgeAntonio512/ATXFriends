//
//  GroupMember.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import Foundation

/// Model representing a member of a group
struct GroupMember: Identifiable, Codable {
    var id: String { userID }
    let userID: String
    var role: GroupMemberRole
    var joinedAt: Date
    var confirmedPlan: Bool
    var joinMessage: String?
    
    enum GroupMemberRole: String, Codable {
        case organizer = "organizer"
        case member = "member"
        case waitlisted = "waitlisted"
        case pending = "pending"
        
        var label: String {
            switch self {
            case .organizer: return "Organizer"
            case .member: return "Member"
            case .waitlisted: return "Waitlisted"
            case .pending: return "Pending"
            }
        }
    }
}
