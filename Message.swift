//
//  Message.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation

// MARK: - Firestore MessageThread

/// Codable version of MessageThread for Firestore operations
struct FirestoreMessageThread: Identifiable, Codable {
    let id: String
    let matchId: String
    let participantIds: [String]
    var lastMessageText: String?
    var lastMessageSenderId: String?
    var lastMessageTimestamp: Date?
    var unreadCount: [String: Int] // userId: count
    var createdAt: Date
    var updatedAt: Date
}

// MARK: - Firestore Message

/// Codable version of Message for Firestore operations
struct FirestoreMessage: Identifiable, Codable {
    let id: String
    let threadId: String
    let senderId: String
    let text: String
    let sentAt: Date
    var isRead: Bool
    var readBy: [String] // Array of user IDs who have read this message
}

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
