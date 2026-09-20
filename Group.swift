//
//  Group.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/3/26.
//

import Foundation

/// Model representing a group activity
struct Group: Identifiable, Codable {
    let id: String
    var activityName: String
    var location: String
    var dateTime: Date
    var recurrence: GroupRecurrence
    var description: String
    var coverPhotoURL: String?
    var minParticipants: Int
    var maxParticipants: Int
    var privacy: GroupPrivacy
    var status: GroupStatus
    var organizerID: String
    var createdAt: Date
    var confirmedAt: Date?
    var confirmationDeadline: Date?
    var geoHash: String
    
    enum GroupRecurrence: String, Codable, CaseIterable {
        case none = "none"
        case weekly = "weekly"
        case biweekly = "biweekly"
        case monthly = "monthly"
        
        var label: String {
            switch self {
            case .none: return "One-time"
            case .weekly: return "Weekly"
            case .biweekly: return "Every two weeks"
            case .monthly: return "Monthly"
            }
        }
    }
    
    enum GroupPrivacy: String, Codable {
        case `public` = "public"
        case inviteOnly = "invite_only"
        
        var label: String {
            switch self {
            case .public: return "Public"
            case .inviteOnly: return "Invite Only"
            }
        }
    }
    
    enum GroupStatus: String, Codable {
        case open = "open"
        case confirming = "confirming"
        case confirmed = "confirmed"
        case canceled = "canceled"
        
        var label: String {
            switch self {
            case .open: return "Open"
            case .confirming: return "Confirming"
            case .confirmed: return "Confirmed"
            case .canceled: return "Canceled"
            }
        }
    }
}
