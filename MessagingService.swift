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
        
        // Check if this looks like an event-prefixed ID
        if matchID.hasPrefix("event_") {
            print("⚠️ WARNING: matchID starts with 'event_' prefix!")
            print("⚠️ WARNING: This suggests EventMessageThreadView should be used instead")
            print("⚠️ WARNING: Event DMs use the 'eventID' field, not 'matchID'")
        }
        
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
                    print("   3. Using wrong view (should use EventMessageThreadView for events)")
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
    
    // MARK: - Event Message Support
    
    /// Structure to hold event thread information
    struct EventThreadInfo {
        let threadID: String
        let eventID: String
        let otherUserID: String
        let lastMessage: Message?
        let unreadCount: Int
    }
    
    /// Fetches all event-based message threads for a user
    /// - Parameter currentUserID: The current user's ID
    /// - Returns: Array of event thread information
    func fetchEventThreads(for currentUserID: String) async throws -> [EventThreadInfo] {
        print("🔍 DEBUG: Fetching event threads for user: \(currentUserID)")
        
        // Query all messages where eventID is not empty and user is sender or receiver
        let snapshot = try await db.collection(messagesCollection)
            .whereField("eventID", isNotEqualTo: "")
            .getDocuments()
        
        print("🔍 DEBUG: Found \(snapshot.documents.count) event messages total")
        
        // Group messages by (eventID, otherUserID) to create threads
        var threadMap: [String: [Message]] = [:]
        
        for document in snapshot.documents {
            guard let message = firestoreDataToMessage(id: document.documentID, data: document.data()),
                  let eventID = message.eventID else {
                continue
            }
            
            // Only include messages where current user is sender or receiver
            var otherUserID: String?
            if message.senderID == currentUserID {
                otherUserID = message.receiverID
            } else if message.receiverID == currentUserID {
                otherUserID = message.senderID
            }
            
            guard let otherUser = otherUserID else {
                continue
            }
            
            // Create a consistent thread key (sorted user IDs to ensure uniqueness)
            let userPair = [currentUserID, otherUser].sorted()
            let threadKey = "event_\(eventID)_\(userPair[0])_\(userPair[1])"
            
            if threadMap[threadKey] == nil {
                threadMap[threadKey] = []
            }
            threadMap[threadKey]?.append(message)
        }
        
        print("🔍 DEBUG: Found \(threadMap.count) unique event threads")
        
        // Convert to EventThreadInfo structures
        var eventThreads: [EventThreadInfo] = []
        
        for (threadKey, messages) in threadMap {
            // Extract eventID and otherUserID from threadKey
            let components = threadKey.components(separatedBy: "_")
            guard components.count >= 4 else { continue }
            
            let eventID = components[1]
            let user1 = components[2]
            let user2 = components[3]
            let otherUserID = (user1 == currentUserID) ? user2 : user1
            
            // Find last message
            let sortedMessages = messages.sorted { $0.sentAt < $1.sentAt }
            let lastMessage = sortedMessages.last
            
            // Count unread messages
            let unreadCount = messages.filter { $0.receiverID == currentUserID && !$0.isRead }.count
            
            let threadInfo = EventThreadInfo(
                threadID: threadKey,
                eventID: eventID,
                otherUserID: otherUserID,
                lastMessage: lastMessage,
                unreadCount: unreadCount
            )
            
            eventThreads.append(threadInfo)
        }
        
        print("🔍 DEBUG: Returning \(eventThreads.count) event threads")
        return eventThreads
    }
    
    /// Fetches all messages for an event thread between two users
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - otherUserID: The other user in the conversation
    ///   - currentUserID: The current user
    /// - Returns: Array of messages sorted by date
    func fetchMessagesForEvent(eventID: String, otherUserID: String, currentUserID: String) async throws -> [Message] {
        // Query messages where eventID matches and users are sender/receiver in either direction
        let snapshot = try await db.collection(messagesCollection)
            .whereField("eventID", isEqualTo: eventID)
            .order(by: "sentAt", descending: false)
            .getDocuments()
        
        var messages: [Message] = []
        
        for document in snapshot.documents {
            if let message = firestoreDataToMessage(id: document.documentID, data: document.data()) {
                // Filter to messages between current user and other user
                let isRelevant = (message.senderID == currentUserID && message.receiverID == otherUserID) ||
                                (message.senderID == otherUserID && message.receiverID == currentUserID)
                
                if isRelevant {
                    messages.append(message)
                }
            }
        }
        
        return messages
    }
    
    /// Sets up a real-time listener for event messages between two users
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - otherUserID: The other user in the conversation
    ///   - currentUserID: The current user
    ///   - completion: Callback with new messages
    /// - Returns: Listener registration (call remove() to stop listening)
    func listenToEventMessages(
        eventID: String,
        otherUserID: String,
        currentUserID: String,
        completion: @escaping ([Message]) -> Void
    ) -> ListenerRegistration {
        print("🟡 DEBUG: Creating Firestore listener for event: \(eventID)")
        
        return db.collection(messagesCollection)
            .whereField("eventID", isEqualTo: eventID)
            .order(by: "sentAt", descending: false)
            .addSnapshotListener { snapshot, error in
                if let error = error {
                    print("🔴 DEBUG: Error listening to event messages: \(error.localizedDescription)")
                    return
                }
                
                guard let snapshot = snapshot else {
                    print("🔴 DEBUG: Snapshot is nil")
                    return
                }
                
                print("🟡 DEBUG: Event Firestore snapshot received!")
                print("🟡 DEBUG: Document count: \(snapshot.documents.count)")
                
                var messages: [Message] = []
                
                for document in snapshot.documents {
                    if let message = self.firestoreDataToMessage(id: document.documentID, data: document.data()) {
                        // Filter to messages between current user and other user
                        let isRelevant = (message.senderID == currentUserID && message.receiverID == otherUserID) ||
                                        (message.senderID == otherUserID && message.receiverID == currentUserID)
                        
                        if isRelevant {
                            messages.append(message)
                            print("🟡 DEBUG: Parsed event message: \(message.text)")
                        }
                    }
                }
                
                print("🟡 DEBUG: Calling completion handler with \(messages.count) event messages")
                completion(messages)
            }
    }
    
    /// Marks all event messages as read for a specific user
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - otherUserID: The other user in the conversation
    ///   - userID: The current user ID (receiver)
    func markEventMessagesAsRead(eventID: String, otherUserID: String, for userID: String) async throws {
        // Check authentication
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: Cannot mark event messages as read - user not authenticated")
            throw NSError(
                domain: "MessagingService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("🔐 DEBUG: markEventMessagesAsRead - User authenticated: \(currentUser.uid)")
        
        let snapshot = try await db.collection(messagesCollection)
            .whereField("eventID", isEqualTo: eventID)
            .whereField("senderID", isEqualTo: otherUserID)
            .whereField("receiverID", isEqualTo: userID)
            .whereField("isRead", isEqualTo: false)
            .getDocuments()
        
        for document in snapshot.documents {
            try await document.reference.updateData(["isRead": true])
        }
    }
}
