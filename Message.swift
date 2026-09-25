//
//  Message.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation

// MARK: - Date Extension for Time Ago

extension Date {
    func timeAgoDisplay() -> String {
        let now = Date()
        let components = Calendar.current.dateComponents([.minute, .hour, .day, .weekOfYear], from: self, to: now)
        
        if let week = components.weekOfYear, week > 0 {
            return week == 1 ? "1w" : "\(week)w"
        } else if let day = components.day, day > 0 {
            return day == 1 ? "1d" : "\(day)d"
        } else if let hour = components.hour, hour > 0 {
            return hour == 1 ? "1h" : "\(hour)h"
        } else if let minute = components.minute, minute > 0 {
            return minute == 1 ? "1m" : "\(minute)m"
        } else {
            return "now"
        }
    }
}
