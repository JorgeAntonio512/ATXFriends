//
//  EventDMHelper.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//  Helper for creating Event DMs from other parts of the app
//

import Foundation
import FirebaseFirestore

/// Helper class for initiating Event DMs
final class EventDMHelper {
    
    /// Sends the first message in an Event DM conversation
    /// Call this when a user wants to start a DM with another attendee from an event
    /// - Parameters:
    ///   - eventID: The event ID (e.g., "ACL2025")
    ///   - receiverID: The ID of the user to message
    ///   - initialMessage: The first message text
    /// - Returns: The created message
    /// - Throws: Firestore errors
    @discardableResult
    static func startEventConversation(
        eventID: String,
        receiverID: String,
        initialMessage: String
    ) async throws -> Message {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else {
            throw NSError(
                domain: "EventDMHelper",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("📱 DEBUG: Starting Event DM conversation")
        print("📱 DEBUG: Event ID: \(eventID)")
        print("📱 DEBUG: Receiver ID: \(receiverID)")
        print("📱 DEBUG: Message: \(initialMessage)")
        
        // Send the message using MessagingService
        let message = try await MessagingService.shared.sendMessage(
            text: initialMessage,
            matchID: "",
            eventID: eventID,
            senderID: currentUserID,
            receiverID: receiverID
        )
        
        print("✅ DEBUG: Event DM conversation started successfully")
        
        return message
    }
    
    /// Checks if a conversation already exists between current user and another user for a specific event
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - otherUserID: The other user's ID
    /// - Returns: True if conversation exists, false otherwise
    static func conversationExists(
        eventID: String,
        otherUserID: String
    ) async -> Bool {
        guard let currentUserID = FirebaseAuthService.shared.currentUserID else {
            return false
        }
        
        do {
            let db = Firestore.firestore()
            
            // Query for any messages between these two users for this event
            let snapshot = try await db.collection("messages")
                .whereField("eventID", isEqualTo: eventID)
                .limit(to: 1)
                .getDocuments()
            
            for document in snapshot.documents {
                let data = document.data()
                guard
                    let senderID = data["senderID"] as? String,
                    let receiverID = data["receiverID"] as? String
                else {
                    continue
                }
                
                // Check if this is a conversation between current user and other user
                if (senderID == currentUserID && receiverID == otherUserID) ||
                   (senderID == otherUserID && receiverID == currentUserID) {
                    return true
                }
            }
            
            return false
        } catch {
            print("❌ ERROR: Failed to check if conversation exists: \(error)")
            return false
        }
    }
}

// MARK: - Example Usage

/*
 
 // Example 1: From Event Detail View - Start a conversation with another attendee
 
 Button("Message \(attendee.displayName)") {
     Task {
         do {
             try await EventDMHelper.startEventConversation(
                 eventID: "ACL2025",
                 receiverID: attendee.id,
                 initialMessage: "Hey! Excited to see you at ACL!"
             )
             
             // Navigate to messages tab or show success message
             showingMessageSuccess = true
         } catch {
             errorMessage = "Failed to send message: \(error.localizedDescription)"
         }
     }
 }
 
 // Example 2: Check if conversation exists before showing "Message" button
 
 @State private var hasExistingConversation = false
 
 .task {
     hasExistingConversation = await EventDMHelper.conversationExists(
         eventID: event.id,
         otherUserID: attendee.id
     )
 }
 
 if hasExistingConversation {
     NavigationLink("Continue Conversation") {
         // Navigate to messages tab with this thread selected
     }
 } else {
     Button("Send Message") {
         // Show message composer
     }
 }
 
 // Example 3: From Event Attendees List
 
 struct EventAttendeesView: View {
     let event: Event
     let attendees: [User]
     
     var body: some View {
         List(attendees) { attendee in
             HStack {
                 // Attendee info...
                 
                 Button {
                     Task {
                         try? await EventDMHelper.startEventConversation(
                             eventID: event.id,
                             receiverID: attendee.id,
                             initialMessage: "Hey! I'm going to \(event.name) too!"
                         )
                     }
                 } label: {
                     Image(systemName: "message.fill")
                         .foregroundColor(.blue)
                 }
             }
         }
     }
 }
 
 // Example 4: Integration with existing MessagingViewModel
 
 struct SomeView: View {
     @State private var messagingViewModel = MessagingViewModel()
     @State private var selectedThread: MessageThread?
     @State private var showingThread = false
     
     var body: some View {
         Button("Message Attendee") {
             Task {
                 // Send first message
                 try? await EventDMHelper.startEventConversation(
                     eventID: "ACL2025",
                     receiverID: "user456",
                     initialMessage: "Hi!"
                 )
                 
                 // Refresh threads to get the new one
                 await messagingViewModel.fetchThreads()
                 
                 // Find and open the thread
                 if let thread = messagingViewModel.eventThreads.first(where: { 
                     $0.event?.id == "ACL2025" && $0.otherUser.id == "user456"
                 }) {
                     selectedThread = thread
                     showingThread = true
                 }
             }
         }
         .sheet(isPresented: $showingThread) {
             if let thread = selectedThread {
                 EventMessageThreadView(thread: thread, viewModel: messagingViewModel)
             }
         }
     }
 }
 
 */
