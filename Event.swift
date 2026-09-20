//
//  Event.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import Foundation
import SwiftData

/// SwiftData model for an event
@Model
final class EventModel {
    /// Event identifier (e.g., "ACL2025")
    @Attribute(.unique) var id: String
    
    /// Event name
    var name: String
    
    /// Hero image URL
    var heroImageURL: String
    
    /// Weekends for this event (stored as raw data)
    @Attribute(.externalStorage) var weekendsData: Data?
    
    init(id: String, name: String, heroImageURL: String, weekends: [EventWeekend] = []) {
        self.id = id
        self.name = name
        self.heroImageURL = heroImageURL
        self.weekendsData = try? JSONEncoder().encode(weekends)
    }
    
    /// Decoded weekends
    var weekends: [EventWeekend] {
        get {
            guard let data = weekendsData else { return [] }
            return (try? JSONDecoder().decode([EventWeekend].self, from: data)) ?? []
        }
        set {
            weekendsData = try? JSONEncoder().encode(newValue)
        }
    }
}

/// Codable struct for Event (for Firebase operations)
struct Event: Identifiable, Codable {
    let id: String
    let name: String
    let heroImageURL: String
    let weekends: [EventWeekend]
    
    init(id: String, name: String, heroImageURL: String, weekends: [EventWeekend]) {
        self.id = id
        self.name = name
        self.heroImageURL = heroImageURL
        self.weekends = weekends
    }
    
    /// Convert from SwiftData model
    init(from model: EventModel) {
        self.id = model.id
        self.name = model.name
        self.heroImageURL = model.heroImageURL
        self.weekends = model.weekends
    }
}

/// Represents a weekend within an event
struct EventWeekend: Codable, Identifiable, Hashable {
    var id: Int { weekendNumber }
    
    let weekendNumber: Int  // 1 or 2
    let label: String
    let startDate: Date
    let endDate: Date
    
    /// Formatted date range (e.g., "Oct 3-5, 2025")
    var dateRangeString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        
        let startString = formatter.string(from: startDate)
        
        formatter.dateFormat = "d, yyyy"
        let endString = formatter.string(from: endDate)
        
        return "\(startString)-\(endString)"
    }
    
    init(weekendNumber: Int, label: String, startDate: Date, endDate: Date) {
        self.weekendNumber = weekendNumber
        self.label = label
        self.startDate = startDate
        self.endDate = endDate
    }
}

/// SwiftData model for Event RSVP
@Model
final class EventRSVPModel {
    /// Document ID (auto-generated)
    @Attribute(.unique) var id: String
    
    /// User's ID
    var userID: String
    
    /// Event ID
    var eventID: String
    
    /// Weekend numbers user RSVPed to (1, 2, or both)
    var weekends: [Int]
    
    /// When the RSVP was created
    var createdAt: Date
    
    init(id: String = UUID().uuidString, userID: String, eventID: String, weekends: [Int], createdAt: Date = Date()) {
        self.id = id
        self.userID = userID
        self.eventID = eventID
        self.weekends = weekends
        self.createdAt = createdAt
    }
}

/// Codable struct for EventRSVP (for Firebase operations)
struct EventRSVP: Identifiable, Codable {
    let id: String
    let userID: String
    let eventID: String
    let weekends: [Int]
    let createdAt: Date
    
    init(id: String = UUID().uuidString, userID: String, eventID: String, weekends: [Int], createdAt: Date = Date()) {
        self.id = id
        self.userID = userID
        self.eventID = eventID
        self.weekends = weekends
        self.createdAt = createdAt
    }
    
    /// Convert from SwiftData model
    init(from model: EventRSVPModel) {
        self.id = model.id
        self.userID = model.userID
        self.eventID = model.eventID
        self.weekends = model.weekends
        self.createdAt = model.createdAt
    }
}

extension EventModel {
    /// Convert from Codable struct
    convenience init(from event: Event) {
        self.init(
            id: event.id,
            name: event.name,
            heroImageURL: event.heroImageURL,
            weekends: event.weekends
        )
    }
}

extension EventRSVPModel {
    /// Convert from Codable struct
    convenience init(from rsvp: EventRSVP) {
        self.init(
            id: rsvp.id,
            userID: rsvp.userID,
            eventID: rsvp.eventID,
            weekends: rsvp.weekends,
            createdAt: rsvp.createdAt
        )
    }
}
