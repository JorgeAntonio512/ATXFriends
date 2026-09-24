//
//  DayOfWeek.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation

/// Days of the week
enum DayOfWeek: String, Codable, CaseIterable, Identifiable {
    case monday = "Monday"
    case tuesday = "Tuesday"
    case wednesday = "Wednesday"
    case thursday = "Thursday"
    case friday = "Friday"
    case saturday = "Saturday"
    case sunday = "Sunday"
    
    var id: String { rawValue }

    /// Stable Monday-first ordering, for deterministic sorts independent of Calendar.weekday.
    var sortIndex: Int {
        switch self {
        case .monday: 0
        case .tuesday: 1
        case .wednesday: 2
        case .thursday: 3
        case .friday: 4
        case .saturday: 5
        case .sunday: 6
        }
    }

    /// Foundation's `Calendar` weekday component (1 = Sunday ... 7 = Saturday).
    var weekdayComponent: Int {
        switch self {
        case .sunday: 1
        case .monday: 2
        case .tuesday: 3
        case .wednesday: 4
        case .thursday: 5
        case .friday: 6
        case .saturday: 7
        }
    }

    /// Reverse of `weekdayComponent` — builds a DayOfWeek from a `Calendar` weekday value.
    static func from(weekdayComponent: Int) -> DayOfWeek {
        switch weekdayComponent {
        case 1: .sunday
        case 2: .monday
        case 3: .tuesday
        case 4: .wednesday
        case 5: .thursday
        case 6: .friday
        default: .saturday
        }
    }
}
