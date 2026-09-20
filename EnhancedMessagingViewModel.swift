//
//  EnhancedMessagingViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/6/26.
//

import Foundation
import FirebaseFirestore
import Observation

/// ViewModel for managing message threads and individual conversations
@Observable
final class EnhancedMessagingViewModel {
    // MARK: - Published Properties
    
    /// All message threads (both match and event)
    var allThreads: [MessageThread] = []
    
    /// Regular match-based DM threads
    var regularThreads: [MessageThread] {
        allThreads.filter { !$0.isEventThread }
    }
    
    /// Event-based DM threads
    var eventThreads: [MessageThread] {
        allThreads.filter { $0.isEventThread }
    }
    
    /// Messages in the currently viewed thread
    var messages: [Message] = []
    
    /// Loading state
    var isLoading = false
    
    /// Error message
    var errorMessage: String?
    
    /// Draft message text
    var draftMessage = ""
    
    /// Whether a message is being sent
    var isSendingMessage = false
    
    // MARK: - Private Properties
    
    private var messagesListener: ListenerRegistration?
    private let db = Firestore.firestore()
    private let messagesCollection = "messages"
    
    private var currentUserID: String {
        FirebaseAuthService.shared.currentUserID ?? ""
    }
    
    // MARK: - Initialization
    
    init() {
        print("📱 DEBUG: MessagingViewModel initialized")
    }
    
    deinit {
        stopListening()
    }
    
    // MARK: - Fetch Threads
    
    /// Fetches all message threads (both regular and event)
    func fetchThreads() async {
        guard !currentUserID.isEmpty else {
            print("❌ ERROR: No current user ID")
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            print("📱 DEBUG: Fetching message threads for user: \(currentUserID)")
            
            // Fetch all messages where user is sender or receiver
            let snapshot = try await db.collection(messagesCollection)
                .order(by: "sentAt", descending: true)
                .getDocuments()
            
            print("📱 DEBUG: Found \(snapshot.documents.count) total messages")
            
            // Group messages by thread (matchID or eventID + otherUserID)
            var threadMap: [String: [Message]] = [:]
            
            for document in snapshot.documents {
                guard let message = firestoreDataToMessage(id: document.documentID, data: document.data()) else {
                    continue
                }
                
                // Only include messages involving current user
                guard message.senderID == currentUserID || message.receiverID == currentUserID else {
                    continue
                }
                
                // Create thread key
                let threadKey: String
                if let eventID = message.eventID, !eventID.isEmpty {
                    // Event thread: use eventID + other user
                    let otherUserID = message.senderID == currentUserID ? message.receiverID : message.senderID
                    threadKey = "event_\(eventID)_\(otherUserID)"
                } else {
                    // Regular thread: use matchID
                    threadKey = "match_\(message.matchID)"
                }
                
                if threadMap[threadKey] == nil {
                    threadMap[threadKey] = []
                }
                threadMap[threadKey]?.append(message)
            }
            
            print("📱 DEBUG: Found \(threadMap.count) unique threads")
            
            // Convert to MessageThread objects
            var threads: [MessageThread] = []
            
            for (_, messagesInThread) in threadMap {
                guard let lastMessage = messagesInThread.first else { continue }
                
                // Determine other user ID
                let otherUserID = lastMessage.senderID == currentUserID ? lastMessage.receiverID : lastMessage.senderID
                
                // Fetch other user
                guard let otherUser = try await FirestoreService.shared.fetchUser(userID: otherUserID)?.toUser() else {
                    print("⚠️ WARNING: Could not fetch user \(otherUserID)")
                    continue
                }
                
                // Calculate unread count
                let unreadCount = messagesInThread.filter { !$0.isRead && $0.receiverID == currentUserID }.count
                
                let thread: MessageThread
                
                if let eventID = lastMessage.eventID, !eventID.isEmpty {
                    // Event thread
                    guard let eventModel = try await fetchEvent(eventID: eventID) else {
                        print("⚠️ WARNING: Could not fetch event \(eventID)")
                        continue
                    }
                    let event = Event(from: eventModel)
                    
                    thread = MessageThread(
                        id: "\(eventID)_\(otherUserID)",
                        match: nil,
                        event: event,
                        otherUser: otherUser,
                        lastMessage: lastMessage,
                        unreadCount: unreadCount
                    )
                } else {
                    // Regular match thread
                    guard let match = try await FirestoreService.shared.fetchMatch(matchID: lastMessage.matchID) else {
                        print("⚠️ WARNING: Could not fetch match \(lastMessage.matchID)")
                        continue
                    }
                    
                    thread = MessageThread(
                        id: lastMessage.matchID,
                        match: match,
                        event: nil,
                        otherUser: otherUser,
                        lastMessage: lastMessage,
                        unreadCount: unreadCount
                    )
                }
                
                threads.append(thread)
            }
            
            // Sort by last message date
            allThreads = threads.sorted { ($0.lastMessage?.sentAt ?? Date.distantPast) > ($1.lastMessage?.sentAt ?? Date.distantPast) }
            
            print("✅ DEBUG: Successfully fetched \(allThreads.count) threads")
            print("📱 DEBUG: Regular threads: \(regularThreads.count)")
            print("📱 DEBUG: Event threads: \(eventThreads.count)")
            
        } catch {
            print("❌ ERROR: Failed to fetch threads: \(error)")
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
    
    // MARK: - Load Messages
    
    /// Loads messages for a specific match
    func loadMessages(for matchID: String) async {
        print("📱 DEBUG: ========================================")
        print("📱 DEBUG: loadMessages(for:) called")
        print("📱 DEBUG: matchID parameter: \(matchID)")
        print("📱 DEBUG: ========================================")
        
        // Check if this looks like it should be an event DM
        if matchID.hasPrefix("event_") {
            print("⚠️ WARNING: matchID starts with 'event_' - this might be wrong!")
            print("⚠️ WARNING: Event DMs should use loadMessagesForEvent() instead")
            print("⚠️ WARNING: This listener will query: whereField('matchID', isEqualTo: '\(matchID)')")
        }
        
        // Start listening to real-time updates
        messagesListener = MessagingService.shared.listenToMessages(for: matchID) { [weak self] messages in
            guard let self = self else { return }
            print("📱 DEBUG: ========================================")
            print("📱 DEBUG: Listener callback received")
            print("📱 DEBUG: Received \(messages.count) messages from listener")
            print("📱 DEBUG: ========================================")
            self.messages = messages
            
            // Mark messages as read
            Task {
                await self.markMessagesAsRead(matchID: matchID)
            }
        }
    }
    
    /// Loads messages for a specific event thread
    func loadMessagesForEvent(eventID: String, otherUserID: String) async {
        print("📱 DEBUG: Loading messages for event: \(eventID), other user: \(otherUserID)")
        
        do {
            let snapshot = try await db.collection(messagesCollection)
                .whereField("eventID", isEqualTo: eventID)
                .order(by: "sentAt", descending: false)
                .getDocuments()
            
            var fetchedMessages: [Message] = []
            
            for document in snapshot.documents {
                if let message = firestoreDataToMessage(id: document.documentID, data: document.data()) {
                    // Only include messages between current user and other user
                    if (message.senderID == currentUserID && message.receiverID == otherUserID) ||
                       (message.senderID == otherUserID && message.receiverID == currentUserID) {
                        fetchedMessages.append(message)
                    }
                }
            }
            
            messages = fetchedMessages
            print("✅ DEBUG: Loaded \(messages.count) event messages")
            
            // Mark as read
            await markEventMessagesAsRead(eventID: eventID, otherUserID: otherUserID)
            
            // Set up real-time listener for event messages
            setupEventMessagesListener(eventID: eventID, otherUserID: otherUserID)
            
        } catch {
            print("❌ ERROR: Failed to load event messages: \(error)")
            errorMessage = error.localizedDescription
        }
    }
    
    /// Sets up a real-time listener for event messages
    private func setupEventMessagesListener(eventID: String, otherUserID: String) {
        messagesListener = db.collection(messagesCollection)
            .whereField("eventID", isEqualTo: eventID)
            .order(by: "sentAt", descending: false)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("❌ ERROR: Event messages listener error: \(error)")
                    return
                }
                
                guard let snapshot = snapshot else { return }
                
                var fetchedMessages: [Message] = []
                
                for document in snapshot.documents {
                    if let message = self.firestoreDataToMessage(id: document.documentID, data: document.data()) {
                        // Only include messages between current user and other user
                        if (message.senderID == self.currentUserID && message.receiverID == otherUserID) ||
                           (message.senderID == otherUserID && message.receiverID == self.currentUserID) {
                            fetchedMessages.append(message)
                        }
                    }
                }
                
                self.messages = fetchedMessages
                
                // Mark new messages as read
                Task {
                    await self.markEventMessagesAsRead(eventID: eventID, otherUserID: otherUserID)
                }
            }
    }
    
    // MARK: - Send Message
    
    /// Sends a message in a match thread
    func sendMessage(matchID: String, receiverID: String) async {
        let messageText = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !messageText.isEmpty else {
            print("⚠️ WARNING: Cannot send empty message")
            return
        }
        
        guard !currentUserID.isEmpty else {
            print("❌ ERROR: No current user ID")
            return
        }
        
        isSendingMessage = true
        
        do {
            print("📱 DEBUG: Sending message in match \(matchID)")
            
            try await MessagingService.shared.sendMessage(
                text: messageText,
                matchID: matchID,
                senderID: currentUserID,
                receiverID: receiverID
            )
            
            // Clear draft
            draftMessage = ""
            
            print("✅ DEBUG: Message sent successfully")
            
        } catch {
            print("❌ ERROR: Failed to send message: \(error)")
            errorMessage = "Failed to send message. Please try again."
        }
        
        isSendingMessage = false
    }
    
    /// Sends a message in an event thread
    func sendEventMessage(eventID: String, receiverID: String) async {
        let messageText = draftMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard !messageText.isEmpty else {
            print("⚠️ WARNING: Cannot send empty message")
            return
        }
        
        guard !currentUserID.isEmpty else {
            print("❌ ERROR: No current user ID")
            return
        }
        
        isSendingMessage = true
        
        do {
            print("📱 DEBUG: Sending event message for event \(eventID)")
            
            try await MessagingService.shared.sendMessage(
                text: messageText,
                matchID: "",
                eventID: eventID,
                senderID: currentUserID,
                receiverID: receiverID
            )
            
            // Clear draft
            draftMessage = ""
            
            print("✅ DEBUG: Event message sent successfully")
            
        } catch {
            print("❌ ERROR: Failed to send event message: \(error)")
            errorMessage = "Failed to send message. Please try again."
        }
        
        isSendingMessage = false
    }
    
    // MARK: - Mark as Read
    
    /// Marks all messages in a match as read
    private func markMessagesAsRead(matchID: String) async {
        do {
            try await MessagingService.shared.markAllAsRead(matchID: matchID, for: currentUserID)
        } catch {
            print("❌ ERROR: Failed to mark messages as read: \(error)")
        }
    }
    
    /// Marks all event messages as read
    private func markEventMessagesAsRead(eventID: String, otherUserID: String) async {
        do {
            let snapshot = try await db.collection(messagesCollection)
                .whereField("eventID", isEqualTo: eventID)
                .whereField("receiverID", isEqualTo: currentUserID)
                .whereField("senderID", isEqualTo: otherUserID)
                .whereField("isRead", isEqualTo: false)
                .getDocuments()
            
            for document in snapshot.documents {
                try await document.reference.updateData(["isRead": true])
            }
        } catch {
            print("❌ ERROR: Failed to mark event messages as read: \(error)")
        }
    }
    
    // MARK: - Listener Management
    
    /// Stops listening to real-time message updates
    func stopListening() {
        print("📱 DEBUG: Stopping message listener")
        messagesListener?.remove()
        messagesListener = nil
    }
    
    // MARK: - Helper Methods
    
    /// Converts Firestore data to Message
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
        
        return Message(
            id: id,
            matchID: matchID,
            eventID: eventID,
            senderID: senderID,
            receiverID: receiverID,
            text: text,
            sentAt: sentAtTimestamp.dateValue(),
            isRead: isRead
        )
    }
    
    /// Fetches an event from Firestore
    private func fetchEvent(eventID: String) async throws -> EventModel? {
        // This assumes there's a method to fetch events from Firestore
        // You may need to add this to FirestoreService
        let document = try await db.collection("events").document(eventID).getDocument()
        
        guard document.exists, let data = document.data() else {
            return nil
        }
        
        guard
            let id = data["id"] as? String,
            let name = data["name"] as? String,
            let heroImageURL = data["heroImageURL"] as? String
        else {
            return nil
        }
        
        // Decode weekends if present
        var weekends: [EventWeekend] = []
        if let weekendsData = data["weekends"] as? [[String: Any]] {
            for weekendDict in weekendsData {
                guard
                    let weekendNumber = weekendDict["weekendNumber"] as? Int,
                    let label = weekendDict["label"] as? String,
                    let startDateTimestamp = weekendDict["startDate"] as? Timestamp,
                    let endDateTimestamp = weekendDict["endDate"] as? Timestamp
                else {
                    continue
                }
                
                let weekend = EventWeekend(
                    weekendNumber: weekendNumber,
                    label: label,
                    startDate: startDateTimestamp.dateValue(),
                    endDate: endDateTimestamp.dateValue()
                )
                weekends.append(weekend)
            }
        }
        
        return EventModel(id: id, name: name, heroImageURL: heroImageURL, weekends: weekends)
    }
}
