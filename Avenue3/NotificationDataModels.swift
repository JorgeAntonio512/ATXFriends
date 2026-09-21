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
    
    private var db: Firestore {
        Firestore.firestore()
    }
}
