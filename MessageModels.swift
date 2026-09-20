//
//  MessageModels.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import Foundation

// MARK: - Message Kind

/// Discriminates between a plain chat message and a plan proposal card.
/// Stored as a raw string in Firestore so new kinds can be added without
/// breaking existing documents — unknown values fall back to `.text` on read.
enum MessageKind: String, Codable {
    case text = "text"
    case planProposal = "planProposal"
}

// MARK: - Message

/// Represents a single message in a conversation
struct Message: Identifiable, Codable {
    /// Unique identifier for the message
    let id: String

    /// Match ID (empty for event messages)
    let matchID: String

    /// Optional: Event ID (for event-based messages)
    let eventID: String?

    /// User who sent the message
    let senderID: String

    /// User who receives the message
    let receiverID: String

    /// Human-readable text; for .planProposal this is the proposal summary
    /// shown in the thread row and used as an accessibility fallback.
    let text: String

    /// When the message was sent
    let sentAt: Date

    /// Whether the message has been read
    var isRead: Bool

    /// Whether this is a plain text message or a plan proposal card.
    /// Absent from legacy documents in Firestore — defaults to .text on read.
    var kind: MessageKind

    /// ID of the corresponding Plan document in the `plans` collection.
    /// Non-nil only when kind == .planProposal. The Plan carries all
    /// proposal details (activity, dates, status) — nothing is duplicated here.
    var planID: String?

    init(
        id: String = UUID().uuidString,
        matchID: String = "",
        eventID: String? = nil,
        senderID: String,
        receiverID: String,
        text: String,
        sentAt: Date = Date(),
        isRead: Bool = false,
        kind: MessageKind = .text,
        planID: String? = nil
    ) {
        self.id = id
        self.matchID = matchID
        self.eventID = eventID
        self.senderID = senderID
        self.receiverID = receiverID
        self.text = text
        self.sentAt = sentAt
        self.isRead = isRead
        self.kind = kind
        self.planID = planID
    }
}

/// Represents a message thread with another user
struct MessageThread: Identifiable, Hashable {
    /// Unique identifier (match ID or eventID)
    let id: String
    
    /// The match (optional for event threads)
    let match: Match?
    
    /// The event (optional for match threads)
    let event: Event?
    
    /// The other user in the conversation
    let otherUser: User
    
    /// Last message in the thread
    var lastMessage: Message?
    
    /// Number of unread messages
    var unreadCount: Int

    /// Next confirmed upcoming plan for this thread, if any.
    /// Nil for event threads — event threads don't carry plan proposals.
    var upcomingPlan: Plan?

    /// Simpatico compatibility score (0–100), or nil if either person hasn't
    /// completed all 18 questions yet. Computed live on load from both
    /// questionnaires — never stored as a precomputed value.
    var simpaticoScore: Int?

    /// Show-up reliability string for the other user (e.g. "100% (1/1)" or "New").
    var showUpMeter: String

    /// Whether this is an event-based thread
    var isEventThread: Bool {
        event != nil
    }
    
    /// Helper to get the other user's first photo URL
    var otherUserPhotoURL: String? {
        otherUser.photoURLs.first
    }
    
    init(
        id: String,
        match: Match? = nil,
        event: Event? = nil,
        otherUser: User,
        lastMessage: Message? = nil,
        unreadCount: Int = 0,
        upcomingPlan: Plan? = nil,
        simpaticoScore: Int? = nil,
        showUpMeter: String = "New"
    ) {
        self.id = id
        self.match = match
        self.event = event
        self.otherUser = otherUser
        self.lastMessage = lastMessage
        self.unreadCount = unreadCount
        self.upcomingPlan = upcomingPlan
        self.simpaticoScore = simpaticoScore
        self.showUpMeter = showUpMeter
    }
    
    // MARK: - Hashable Conformance
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    static func == (lhs: MessageThread, rhs: MessageThread) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Display Helpers

extension MessageThread {
    /// Short label shown in the thread list row when there is a confirmed
    /// upcoming plan, e.g. "Poker · Sat 7pm". Returns nil when no plan exists
    /// so callers can fall back to the last-message preview.
    var planPillText: String? {
        guard let plan = upcomingPlan, let date = plan.confirmedDate else { return nil }
        let cal = Calendar.current
        let dayStr: String
        if cal.isDateInToday(date) {
            dayStr = "Today"
        } else if cal.isDateInTomorrow(date) {
            dayStr = "Tmrw"
        } else {
            let f = DateFormatter()
            f.dateFormat = "EEE"
            dayStr = f.string(from: date)
        }
        let tf = DateFormatter()
        tf.dateFormat = "h:mma"
        tf.amSymbol = "am"
        tf.pmSymbol = "pm"
        // "7:00am" → "7am", "7:30am" stays "7:30am"
        let timeStr = tf.string(from: date).replacingOccurrences(of: ":00", with: "")
        return "\(plan.activity.name) · \(dayStr) \(timeStr)"
    }

    /// Returns the display text for the last message
    var displayText: String {
        guard let lastMessage = lastMessage else {
            return "No messages yet"
        }
        return lastMessage.text
    }
    
    /// Returns a formatted time string for the last message
    var timeText: String {
        guard let lastMessage = lastMessage else {
            return ""
        }
        
        let calendar = Calendar.current
        let now = Date()
        let sentAt = lastMessage.sentAt
        
        if calendar.isDateInToday(sentAt) {
            // Show time for today (e.g., "2:30 PM")
            let formatter = DateFormatter()
            formatter.dateFormat = "h:mm a"
            return formatter.string(from: sentAt)
        } else if calendar.isDateInYesterday(sentAt) {
            return "Yesterday"
        } else if let daysDifference = calendar.dateComponents([.day], from: sentAt, to: now).day,
                  daysDifference < 7 {
            // Show day of week for last 7 days (e.g., "Monday")
            let formatter = DateFormatter()
            formatter.dateFormat = "EEEE"
            return formatter.string(from: sentAt)
        } else {
            // Show date for older messages (e.g., "5/1/26")
            let formatter = DateFormatter()
            formatter.dateFormat = "M/d/yy"
            return formatter.string(from: sentAt)
        }
    }
}
