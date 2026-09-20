//
//  DaySlotCombo.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import SwiftData

/// SwiftData model version of DaySlotCombo (for local persistence)
@Model
final class DaySlotComboModel: Hashable, Equatable {
    // SwiftData-compatible stored properties (raw values)
    private var dayOfWeekRawValue: String
    private var timeSlotRawValue: String
    
    // Computed properties to convert back to enums
    var dayOfWeek: DayOfWeek {
        get { DayOfWeek(rawValue: dayOfWeekRawValue) ?? .monday }
        set { dayOfWeekRawValue = newValue.rawValue }
    }
    
    var timeSlot: TimeSlot {
        get { TimeSlot(rawValue: timeSlotRawValue) ?? .wakeUp }
        set { timeSlotRawValue = newValue.rawValue }
    }
    
    init(dayOfWeek: DayOfWeek, timeSlot: TimeSlot) {
        self.dayOfWeekRawValue = dayOfWeek.rawValue
        self.timeSlotRawValue = timeSlot.rawValue
    }
    
    /// Display name for the combo (e.g. "Monday Night")
    var displayName: String {
        "\(dayOfWeek.rawValue) \(timeSlot.rawValue)"
    }
    
    /// Full display with icon (e.g. "🌙 Monday Night")
    var displayNameWithIcon: String {
        "\(timeSlot.icon) \(displayName)"
    }
    
    // MARK: - Hashable & Equatable
    
    static func == (lhs: DaySlotComboModel, rhs: DaySlotComboModel) -> Bool {
        lhs.dayOfWeekRawValue == rhs.dayOfWeekRawValue &&
        lhs.timeSlotRawValue == rhs.timeSlotRawValue
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(dayOfWeekRawValue)
        hasher.combine(timeSlotRawValue)
    }
}

/// Codable struct version of DaySlotCombo (for Firebase/network operations)
struct DaySlotCombo: Codable, Hashable {
    let dayOfWeek: DayOfWeek
    let timeSlot: TimeSlot
    
    /// Display name like "Monday Night" or "Saturday Wake Up"
    var displayName: String {
        "\(dayOfWeek.rawValue) \(timeSlot.rawValue)"
    }
    
    /// Full display with icon (e.g. "🌙 Monday Night")
    var displayNameWithIcon: String {
        "\(timeSlot.icon) \(displayName)"
    }
    
    init(dayOfWeek: DayOfWeek, timeSlot: TimeSlot) {
        self.dayOfWeek = dayOfWeek
        self.timeSlot = timeSlot
    }
    
    /// Convert from SwiftData model to Codable struct
    init(from model: DaySlotComboModel) {
        self.dayOfWeek = model.dayOfWeek
        self.timeSlot = model.timeSlot
    }
    
    /// Parse from string format "DayOfWeek_TimeSlot" (e.g., "Monday_Morning")
    init?(from string: String) {
        let parts = string.split(separator: "_")
        guard parts.count == 2,
              let day = DayOfWeek(rawValue: String(parts[0])),
              let slot = TimeSlot(rawValue: String(parts[1])) else {
            return nil
        }
        self.dayOfWeek = day
        self.timeSlot = slot
    }
}

extension DaySlotComboModel {
    /// Convert from Codable struct to SwiftData model
    convenience init(from combo: DaySlotCombo) {
        self.init(dayOfWeek: combo.dayOfWeek, timeSlot: combo.timeSlot)
    }
}
