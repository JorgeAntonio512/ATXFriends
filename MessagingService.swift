//
//  MessagingService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseFirestore
import FirebaseAuth
import Combine

/// Service for managing messages and real-time messaging
final class MessagingService {
    // MARK: - Properties
    
    private let db = Firestore.firestore()
    private let messagesCollection = "messages"
    
    // MARK: - Singleton
    
    static let shared = MessagingService()
    
    private init() {}
    
    // MARK: - Send Message
    
    /// Sends a message in a match thread or event thread
    /// - Parameters:
    ///   - text: Message text
    ///   - matchID: The match ID (required - can be regular match ID or event-prefixed thread ID)
    ///   - eventID: The event ID (optional - only for event messages)
    ///   - senderID: User sending the message
    ///   - receiverID: User receiving the message
    /// - Returns: The created message
    @discardableResult
    func sendMessage(
        text: String,
        matchID: String? = nil,
        eventID: String? = nil,
        senderID: String,
        receiverID: String
    ) async throws -> Message {
        print("📤 DEBUG: ========================================")
        print("📤 DEBUG: Sending message...")
        print("📤 DEBUG: ========================================")
        print("📤 DEBUG: matchID: \(matchID ?? "nil")")
        print("📤 DEBUG: eventID: \(eventID ?? "nil")")
        print("📤 DEBUG: senderID: \(senderID)")
        print("📤 DEBUG: receiverID: \(receiverID)")
        print("📤 DEBUG: text: \(text)")
        
        // Determine message type
        if let eventID = eventID, !eventID.isEmpty {
            print("✅ DEBUG: This is an EVENT DM")
            print("✅ DEBUG: - eventID: \(eventID)")
            print("✅ DEBUG: - matchID: \(matchID ?? "nil") (event-prefixed thread ID)")
        } else if let matchID = matchID, !matchID.isEmpty {
            print("✅ DEBUG: This is a REGULAR DM")
            print("✅ DEBUG: - matchID: \(matchID)")
        } else {
            print("⚠️ WARNING: Neither matchID nor eventID provided!")
        }
        print("📤 DEBUG: ========================================")
        
        // ✅ CRITICAL: Check authentication state BEFORE attempting Firestore write
        print("🔐 DEBUG: Checking Firebase Auth state...")
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: NO USER AUTHENTICATED - This will cause permission denied!")
            print("❌ DEBUG: Auth.auth().currentUser is nil")
            throw NSError(
                domain: "MessagingService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated to send messages"]
            )
        }
        
        print("✅ DEBUG: User IS authenticated!")
        print("✅ DEBUG: Current user UID: \(currentUser.uid)")
        print("✅ DEBUG: Current user email: \(currentUser.email ?? "no email")")
        print("✅ DEBUG: Current user isAnonymous: \(currentUser.isAnonymous)")
        print("✅ DEBUG: Sender ID matches current user: \(senderID == currentUser.uid)")
        
        // Verify senderID matches authenticated user
        if senderID != currentUser.uid {
            print("⚠️ WARNING: senderID (\(senderID)) does not match authenticated user (\(currentUser.uid))")
        }
        
        let message = Message(
            matchID: matchID ?? "",
            eventID: eventID,
            senderID: senderID,
            receiverID: receiverID,
            text: text,
            sentAt: Date(),
            isRead: false
        )
        
        var messageData = messageToFirestoreData(message)
        
        // Add eventID if provided
        if let eventID = eventID {
            messageData["eventID"] = eventID
        }
        
        print("📤 DEBUG: ========================================")
        print("📤 DEBUG: Final message data to be written:")
        print("📤 DEBUG: Document ID: \(message.id)")
        print("📤 DEBUG: Collection path: \(messagesCollection)")
        print("📤 DEBUG: Data:")
        for (key, value) in messageData {
            print("📤 DEBUG:   - \(key): \(value)")
        }
        print("📤 DEBUG: ========================================")
        
        // Add error handling to capture the exact Firestore error
        do {
            print("📤 DEBUG: About to write to Firestore...")
            
            try await db.collection(messagesCollection)
                .document(message.id)
                .setData(messageData)
            
            print("✅ DEBUG: ========================================")
            print("✅ DEBUG: Message sent successfully!")
            print("✅ DEBUG: Document \(message.id) created in Firestore")
            print("✅ DEBUG: ========================================")
            return message
        } catch {
            print("❌ DEBUG: ========================================")
            print("❌ DEBUG: Firestore write FAILED!")
            print("❌ DEBUG: Error domain: \((error as NSError).domain)")
            print("❌ DEBUG: Error code: \((error as NSError).code)")
            print("❌ DEBUG: Error description: \(error.localizedDescription)")
            print("❌ DEBUG: Full error: \(error)")
            print("❌ DEBUG: ========================================")
            throw error
        }
    }
    
    // MARK: - Send Plan Proposal

    /// Writes a plan-proposal message into a match thread.
    ///
    /// The caller (ViewModel) is responsible for creating the Plan document in
    /// Firestore via PlansService first, then passing its ID here.  This method
    /// only writes the message document that references that plan — it never
    /// duplicates plan data.
    ///
    /// - Parameters:
    ///   - planID: The document ID of the Plan already saved to the `plans` collection.
    ///   - summary: Short human-readable label shown in the thread row and as an
    ///              accessibility fallback, e.g. "Proposed Hiking · Sat Aug 30, 7 PM".
    ///   - matchID: The match ID for the conversation.
    ///   - senderID: User proposing the plan.
    ///   - receiverID: User receiving the proposal.
    @discardableResult
    func sendPlanProposal(
        planID: String,
        summary: String,
        matchID: String,
        senderID: String,
        receiverID: String
    ) async throws -> Message {
        guard Auth.auth().currentUser != nil else {
            throw NSError(
                domain: "MessagingService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated to send messages"]
            )
        }

        let message = Message(
            matchID: matchID,
            senderID: senderID,
            receiverID: receiverID,
            text: summary,
            isRead: false,
            kind: .planProposal,
            planID: planID
        )

        let data = messageToFirestoreData(message)
        try await db.collection(messagesCollection).document(message.id).setData(data)
        return message
    }

    // MARK: - Fetch Messages
    
    /// Fetches all messages for a match
    /// - Parameter matchID: The match ID
    /// - Returns: Array of messages sorted by date
    func fetchMessages(for matchID: String) async throws -> [Message] {
        let snapshot = try await db.collection(messagesCollection)
            .whereField("matchID", isEqualTo: matchID)
            .order(by: "sentAt", descending: false)
            .getDocuments()
        
        var messages: [Message] = []
        
        for document in snapshot.documents {
            if let message = firestoreDataToMessage(id: document.documentID, data: document.data()) {
                messages.append(message)
            }
        }
        
        return messages
    }
    
    // MARK: - Real-time Listener
    
    /// Sets up a real-time listener for new messages in a match
    /// - Parameters:
    ///   - matchID: The match ID to listen to
    ///   - completion: Callback with new messages
    /// - Returns: Listener registration (call remove() to stop listening)
    func listenToMessages(
        for matchID: String,
        completion: @escaping ([Message]) -> Void
    ) -> ListenerRegistration {
        print("🟡 DEBUG: ========================================")
        print("🟡 DEBUG: Creating Firestore listener")
        print("🟡 DEBUG: ========================================")
        print("🟡 DEBUG: matchID parameter: \(matchID)")
        print("🟡 DEBUG: Collection path: \(messagesCollection)")
        print("🟡 DEBUG: Query: whereField('matchID', isEqualTo: '\(matchID)')")
        print("🟡 DEBUG: ========================================")
        
        return db.collection(messagesCollection)
            .whereField("matchID", isEqualTo: matchID)
            .order(by: "sentAt", descending: false)
            .addSnapshotListener { snapshot, error in
                if let error = error {
                    print("🔴 DEBUG: ❌ Error listening to messages: \(error.localizedDescription)")
                    print("🔴 DEBUG: Error code: \((error as NSError).code)")
                    print("🔴 DEBUG: Full error: \(error)")
                    return
                }
                
                guard let snapshot = snapshot else {
                    print("🔴 DEBUG: ❌ Snapshot is nil")
                    return
                }
                
                print("🟡 DEBUG: ✅ Firestore snapshot received!")
                print("🟡 DEBUG: Document count: \(snapshot.documents.count)")
                print("🟡 DEBUG: Metadata - hasPendingWrites: \(snapshot.metadata.hasPendingWrites)")
                print("🟡 DEBUG: Metadata - isFromCache: \(snapshot.metadata.isFromCache)")
                
                // Log all documents for debugging
                if snapshot.documents.isEmpty {
                    print("⚠️ WARNING: No documents found for matchID: \(matchID)")
                    print("⚠️ WARNING: Possible reasons:")
                    print("   1. No messages sent yet")
                    print("   2. matchID value mismatch")
                }
                
                var messages: [Message] = []
                
                for (index, document) in snapshot.documents.enumerated() {
                    print("🟡 DEBUG: Document [\(index + 1)/\(snapshot.documents.count)]:")
                    print("🟡 DEBUG:   - ID: \(document.documentID)")
                    
                    let data = document.data()
                    print("🟡 DEBUG:   - matchID in Firestore: \(data["matchID"] as? String ?? "nil")")
                    print("🟡 DEBUG:   - eventID in Firestore: \(data["eventID"] as? String ?? "nil")")
                    print("🟡 DEBUG:   - senderID: \(data["senderID"] as? String ?? "nil")")
                    print("🟡 DEBUG:   - receiverID: \(data["receiverID"] as? String ?? "nil")")
                    
                    if let message = self.firestoreDataToMessage(id: document.documentID, data: data) {
                        messages.append(message)
                        print("🟡 DEBUG:   - ✅ Parsed successfully")
                        print("🟡 DEBUG:   - Text: \(message.text)")
                    } else {
                        print("🔴 DEBUG:   - ❌ Failed to parse")
                    }
                }
                
                print("🟡 DEBUG: ========================================")
                print("🟡 DEBUG: Listener summary:")
                print("🟡 DEBUG: Querying matchID: \(matchID)")
                print("🟡 DEBUG: Found \(messages.count) messages")
                print("🟡 DEBUG: ========================================")
                completion(messages)
            }
    }
    
    // MARK: - Mark as Read
    
    /// Marks a message as read
    /// - Parameter messageID: The message ID
    func markAsRead(messageID: String) async throws {
        // Check authentication
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: Cannot mark message as read - user not authenticated")
            throw NSError(
                domain: "MessagingService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("🔐 DEBUG: markAsRead - User authenticated: \(currentUser.uid)")
        
        try await db.collection(messagesCollection)
            .document(messageID)
            .updateData(["isRead": true])
    }
    
    /// Marks all messages in a match as read for a specific user
    /// - Parameters:
    ///   - matchID: The match ID
    ///   - userID: The user ID (receiver)
    func markAllAsRead(matchID: String, for userID: String) async throws {
        // Check authentication
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: Cannot mark messages as read - user not authenticated")
            throw NSError(
                domain: "MessagingService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("🔐 DEBUG: markAllAsRead - User authenticated: \(currentUser.uid)")
        print("🔐 DEBUG: markAllAsRead - Marking messages for userID: \(userID)")
        
        let snapshot = try await db.collection(messagesCollection)
            .whereField("matchID", isEqualTo: matchID)
            .whereField("receiverID", isEqualTo: userID)
            .whereField("isRead", isEqualTo: false)
            .getDocuments()
        
        for document in snapshot.documents {
            try await document.reference.updateData(["isRead": true])
        }
    }
    
    // MARK: - Get Last Message
    
    /// Fetches the last message for a match
    /// - Parameter matchID: The match ID
    /// - Returns: The most recent message, if any
    func getLastMessage(for matchID: String) async throws -> Message? {
        let snapshot = try await db.collection(messagesCollection)
            .whereField("matchID", isEqualTo: matchID)
            .order(by: "sentAt", descending: true)
            .limit(to: 1)
            .getDocuments()
        
        guard let document = snapshot.documents.first else {
            return nil
        }
        
        return firestoreDataToMessage(id: document.documentID, data: document.data())
    }
    
    // MARK: - Get Unread Count
    
    /// Gets the count of unread messages for a user in a match
    /// - Parameters:
    ///   - matchID: The match ID
    ///   - userID: The user ID (receiver)
    /// - Returns: Number of unread messages
    func getUnreadCount(matchID: String, for userID: String) async throws -> Int {
        let snapshot = try await db.collection(messagesCollection)
            .whereField("matchID", isEqualTo: matchID)
            .whereField("receiverID", isEqualTo: userID)
            .whereField("isRead", isEqualTo: false)
            .getDocuments()
        
        return snapshot.documents.count
    }
    
    // MARK: - Data Conversion
    
    /// Converts a Message to Firestore data
    private func messageToFirestoreData(_ message: Message) -> [String: Any] {
        var data: [String: Any] = [
            "senderID": message.senderID,
            "receiverID": message.receiverID,
            "text": message.text,
            "sentAt": Timestamp(date: message.sentAt),
            "isRead": message.isRead
        ]

        if !message.matchID.isEmpty {
            data["matchID"] = message.matchID
        }

        if let eventID = message.eventID, !eventID.isEmpty {
            data["eventID"] = eventID
        }

        // Write kind only for non-text messages so legacy documents stay unchanged.
        if message.kind != .text {
            data["kind"] = message.kind.rawValue
        }

        if let planID = message.planID {
            data["planID"] = planID
        }

        return data
    }
    
    /// Converts Firestore data to a Message
    private func firestoreDataToMessage(id: String, data: [String: Any]) -> Message? {
        guard
            let senderID = data["senderID"] as? String,
            let receiverID = data["receiverID"] as? String,
            let text = data["text"] as? String,
            let sentAtTimestamp = data["sentAt"] as? Timestamp,
            let isRead = data["isRead"] as? Bool
        else {
            return nil
        }

        let matchID = data["matchID"] as? String ?? ""
        let eventID = data["eventID"] as? String

        // Legacy documents have no "kind" field — default to .text so they
        // continue to render as plain chat bubbles without any migration.
        let kind: MessageKind
        if let rawKind = data["kind"] as? String, let parsed = MessageKind(rawValue: rawKind) {
            kind = parsed
        } else {
            kind = .text
        }

        let planID = data["planID"] as? String

        return Message(
            id: id,
            matchID: matchID,
            eventID: eventID,
            senderID: senderID,
            receiverID: receiverID,
            text: text,
            sentAt: sentAtTimestamp.dateValue(),
            isRead: isRead,
            kind: kind,
            planID: planID
        )
    }
}
