//
//  NotificationDataModels.swift
//  Avenue3
//
//  Data models for notification-related Firestore collections
//  Created by George Anthony Pazdral II on 5/24/26.
//

import Foundation
import FirebaseMessaging
import FirebaseFirestore

// MARK: - Conversation Models

/// Represents a conversation between two users
struct Conversation: Identifiable, Codable {
    /// Unique identifier for the conversation
    let id: String
    
    /// Array of participant user IDs (should be 2 users)
    var participants: [String]
    
    /// The last message timestamp (for sorting)
    var lastMessageAt: Date
    
    /// Optional: Last message preview text
    var lastMessageText: String?
    
    /// Optional: ID of user who sent last message
    var lastMessageSenderId: String?
    
    /// Creation timestamp
    var createdAt: Date
    
    init(
        id: String = UUID().uuidString,
        participants: [String],
        lastMessageAt: Date = Date(),
        lastMessageText: String? = nil,
        lastMessageSenderId: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.participants = participants
        self.lastMessageAt = lastMessageAt
        self.lastMessageText = lastMessageText
        self.lastMessageSenderId = lastMessageSenderId
        self.createdAt = createdAt
    }
}

/// Represents a single message within a conversation (Firestore model)
struct ConversationMessage: Identifiable, Codable {
    /// Unique identifier for the message
    let id: String
    
    /// ID of the user who sent the message
    let senderId: String
    
    /// Message text content
    var text: String
    
    /// When the message was sent
    var createdAt: Date
    
    /// Optional: Whether the message has been read
    var isRead: Bool
    
    init(
        id: String = UUID().uuidString,
        senderId: String,
        text: String,
        createdAt: Date = Date(),
        isRead: Bool = false
    ) {
        self.id = id
        self.senderId = senderId
        self.text = text
        self.createdAt = createdAt
        self.isRead = isRead
    }
}

// MARK: - Hangout Models

/// Status of a hangout request (Firestore model)
enum HangoutStatus: String, Codable {
    case pending = "pending"
    case confirmed = "confirmed"
    case declined = "declined"
    case cancelled = "cancelled"
    case completed = "completed"
}

/// Represents a hangout between two users (Firestore model)
struct Hangout: Identifiable, Codable {
    /// Unique identifier for the plan
    let id: String
    
    /// ID of the user who created the plan (initiated the request)
    let creatorId: String
    
    /// ID of the user who was invited to the plan
    let invitedUserId: String
    
    /// Name of the activity (e.g., "Coffee", "Hiking", "Lunch")
    var activityName: String
    
    /// Current status of the hangout
    var status: HangoutStatus
    
    /// Optional: ID of user who confirmed (if status is confirmed)
    var confirmedById: String?
    
    /// Optional: Scheduled date and time for the plan
    var dateTime: Date?
    
    /// Optional: Location name
    var locationName: String?
    
    /// Optional: Location coordinates
    var locationLatitude: Double?
    var locationLongitude: Double?
    
    /// Optional: Additional notes
    var notes: String?
    
    /// Creation timestamp
    var createdAt: Date
    
    /// Last update timestamp
    var updatedAt: Date
    
    init(
        id: String = UUID().uuidString,
        creatorId: String,
        invitedUserId: String,
        activityName: String,
        status: HangoutStatus = .pending,
        confirmedById: String? = nil,
        dateTime: Date? = nil,
        locationName: String? = nil,
        locationLatitude: Double? = nil,
        locationLongitude: Double? = nil,
        notes: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.creatorId = creatorId
        self.invitedUserId = invitedUserId
        self.activityName = activityName
        self.status = status
        self.confirmedById = confirmedById
        self.dateTime = dateTime
        self.locationName = locationName
        self.locationLatitude = locationLatitude
        self.locationLongitude = locationLongitude
        self.notes = notes
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

// MARK: - Firestore Service Extensions

extension FirestoreService {
    // MARK: - Conversation Operations
    
    /// Creates a new conversation in Firestore
    func createConversation(_ conversation: Conversation) async throws {
        let data: [String: Any] = [
            "participants": conversation.participants,
            "lastMessageAt": Timestamp(date: conversation.lastMessageAt),
            "lastMessageText": conversation.lastMessageText ?? "",
            "lastMessageSenderId": conversation.lastMessageSenderId ?? "",
            "createdAt": Timestamp(date: conversation.createdAt)
        ]
        
        try await db.collection("conversations").document(conversation.id).setData(data)
    }
    
    /// Fetches all conversations for a user
    func fetchConversations(for userID: String) async throws -> [Conversation] {
        let snapshot = try await db.collection("conversations")
            .whereField("participants", arrayContains: userID)
            .order(by: "lastMessageAt", descending: true)
            .getDocuments()
        
        return snapshot.documents.compactMap { doc in
            try? doc.data(as: Conversation.self)
        }
    }
    
    /// Sends a message in a conversation
    func sendMessage(_ message: ConversationMessage, in conversationID: String) async throws {
        let messageData: [String: Any] = [
            "senderId": message.senderId,
            "text": message.text,
            "createdAt": Timestamp(date: message.createdAt),
            "isRead": message.isRead
        ]
        
        // Add message to conversation's messages subcollection
        try await db.collection("conversations")
            .document(conversationID)
            .collection("messages")
            .document(message.id)
            .setData(messageData)
        
        // Update conversation's lastMessageAt
        try await db.collection("conversations")
            .document(conversationID)
            .updateData([
                "lastMessageAt": Timestamp(date: message.createdAt),
                "lastMessageText": message.text,
                "lastMessageSenderId": message.senderId
            ])
    }
    
    /// Fetches messages for a conversation
    func fetchMessages(for conversationID: String) async throws -> [ConversationMessage] {
        let snapshot = try await db.collection("conversations")
            .document(conversationID)
            .collection("messages")
            .order(by: "createdAt", descending: false)
            .getDocuments()
        
        return snapshot.documents.compactMap { doc in
            try? doc.data(as: ConversationMessage.self)
        }
    }
    
    // MARK: - Hangout Operations
    
    /// Creates a new hangout in Firestore
    func createPlan(_ plan: Hangout) async throws {
        var data: [String: Any] = [
            "creatorId": plan.creatorId,
            "invitedUserId": plan.invitedUserId,
            "activityName": plan.activityName,
            "status": plan.status.rawValue,
            "createdAt": Timestamp(date: plan.createdAt),
            "updatedAt": Timestamp(date: plan.updatedAt)
        ]
        
        // Add optional fields
        if let confirmedById = plan.confirmedById {
            data["confirmedById"] = confirmedById
        }
        if let dateTime = plan.dateTime {
            data["dateTime"] = Timestamp(date: dateTime)
        }
        if let locationName = plan.locationName {
            data["locationName"] = locationName
        }
        if let latitude = plan.locationLatitude {
            data["locationLatitude"] = latitude
        }
        if let longitude = plan.locationLongitude {
            data["locationLongitude"] = longitude
        }
        if let notes = plan.notes {
            data["notes"] = notes
        }
        
        try await db.collection("plans").document(plan.id).setData(data)
    }
    
    /// Updates an existing hangout
    func updatePlan(_ plan: Hangout) async throws {
        var data: [String: Any] = [
            "status": plan.status.rawValue,
            "updatedAt": Timestamp(date: Date())
        ]
        
        // Add optional fields that may have changed
        if let confirmedById = plan.confirmedById {
            data["confirmedById"] = confirmedById
        }
        if let dateTime = plan.dateTime {
            data["dateTime"] = Timestamp(date: dateTime)
        }
        if let locationName = plan.locationName {
            data["locationName"] = locationName
        }
        if let latitude = plan.locationLatitude {
            data["locationLatitude"] = latitude
        }
        if let longitude = plan.locationLongitude {
            data["locationLongitude"] = longitude
        }
        if let notes = plan.notes {
            data["notes"] = notes
        }
        
        try await db.collection("plans").document(plan.id).updateData(data)
    }
    
    /// Fetches all hangouts for a user
    func fetchPlans(for userID: String) async throws -> [Hangout] {
        // Query for plans where user is creator or invitee
        let creatorQuery = db.collection("plans")
            .whereField("creatorId", isEqualTo: userID)
        
        let inviteeQuery = db.collection("plans")
            .whereField("invitedUserId", isEqualTo: userID)
        
        let (creatorSnapshot, inviteeSnapshot) = try await (
            creatorQuery.getDocuments(),
            inviteeQuery.getDocuments()
        )
        
        var plans: [Hangout] = []
        
        for doc in creatorSnapshot.documents {
            if let plan = try? doc.data(as: Hangout.self) {
                plans.append(plan)
            }
        }
        
        for doc in inviteeSnapshot.documents {
            if let plan = try? doc.data(as: Hangout.self) {
                plans.append(plan)
            }
        }
        
        // Remove duplicates and sort by creation date
        let uniquePlans = Array(Set(plans.map { $0.id }))
            .compactMap { id in plans.first { $0.id == id } }
            .sorted { $0.createdAt > $1.createdAt }
        
        return uniquePlans
    }
    
    /// Confirms a plan
    func confirmPlan(_ planID: String, confirmedBy userID: String) async throws {
        try await db.collection("plans")
            .document(planID)
            .updateData([
                "status": HangoutStatus.confirmed.rawValue,
                "confirmedById": userID,
                "updatedAt": Timestamp(date: Date())
            ])
    }
    
    /// Declines a plan
    func declinePlan(_ planID: String) async throws {
        try await db.collection("plans")
            .document(planID)
            .updateData([
                "status": HangoutStatus.declined.rawValue,
                "updatedAt": Timestamp(date: Date())
            ])
    }
    
    private var db: Firestore {
        Firestore.firestore()
    }
}

// MARK: - Helper Extensions

extension Hangout: Hashable {
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
    
    static func == (lhs: Hangout, rhs: Hangout) -> Bool {
        lhs.id == rhs.id
    }
}
