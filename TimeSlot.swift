//
//  TimeSlot.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation

/// Represents the 5 named time slots throughout the day
enum TimeSlot: String, Codable, CaseIterable, Identifiable {
    case wakeUp = "Wake Up"
    case afternoon = "Afternoon"
    case evening = "Evening"
    case night = "Night"
    case owlHours = "Owl Hours"
    
    var id: String { rawValue }
    
    /// The emoji icon for this time slot
    var icon: String {
        switch self {
        case .wakeUp: "☀️"
        case .afternoon: "🌤"
        case .evening: "🌆"
        case .night: "🌙"
        case .owlHours: "🦉"
        }
    }
    
    /// The time range for this slot
    var timeRange: String {
        switch self {
        case .wakeUp: "7:00am – 12:00pm"
        case .afternoon: "12:00pm – 5:00pm"
        case .evening: "5:00pm – 9:00pm"
        case .night: "9:00pm – 2:00am"
        case .owlHours: "2:00am – 7:00am"
        }
    }
    
    /// Full display name with icon
    var displayName: String {
        "\(icon) \(rawValue)"
    }
}
