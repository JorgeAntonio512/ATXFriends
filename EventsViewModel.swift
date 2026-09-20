//
//  EventsViewModel.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 6/6/26.
//

import Foundation
import FirebaseFirestore

/// ViewModel for managing events and RSVPs
@Observable
final class EventsViewModel {
    // MARK: - Published State
    
    /// All available events
    var events: [Event] = []
    
    /// User's RSVPs
    var userRSVPs: [EventRSVP] = []
    
    /// Attendees for the current event (filtered by user's RSVPed weekends)
    var attendees: [FirebaseUser] = []
    
    /// Error message
    var errorMessage: String?
    
    /// Loading state
    var isLoading: Bool = false
    
    // MARK: - Services
    
    private let db = Firestore.firestore()
    private let firestoreService = FirestoreService.shared
    private let authService = FirebaseAuthService.shared
    
    // MARK: - Computed Properties
    
    /// Current user's ID
    private var currentUserID: String {
        authService.currentUserID ?? ""
    }
    
    // MARK: - Load Events
    
    /// Fetches all events from Firestore
    @MainActor
    func loadEvents() async {
        isLoading = true
        defer { isLoading = false }
        
        do {
            let snapshot = try await db.collection("events").getDocuments()
            
            var loadedEvents: [Event] = []
            
            for document in snapshot.documents {
                if let event = parseEvent(id: document.documentID, data: document.data()) {
                    loadedEvents.append(event)
                }
            }
            
            events = loadedEvents
            
            // Also load user's RSVPs
            await loadUserRSVPs()
            
        } catch {
            errorMessage = "Failed to load events: \(error.localizedDescription)"
        }
    }
    
    /// Parses a Firestore event document
    private func parseEvent(id: String, data: [String: Any]) -> Event? {
        guard
            let name = data["name"] as? String,
            let heroImageURL = data["heroImageURL"] as? String,
            let weekendsArray = data["weekends"] as? [[String: Any]]
        else {
            return nil
        }
        
        var weekends: [EventWeekend] = []
        
        for (index, weekendData) in weekendsArray.enumerated() {
            guard
                let label = weekendData["label"] as? String,
                let startTimestamp = weekendData["startDate"] as? Timestamp,
                let endTimestamp = weekendData["endDate"] as? Timestamp
            else {
                continue
            }
            
            let weekend = EventWeekend(
                weekendNumber: index + 1,
                label: label,
                startDate: startTimestamp.dateValue(),
                endDate: endTimestamp.dateValue()
            )
            weekends.append(weekend)
        }
        
        return Event(id: id, name: name, heroImageURL: heroImageURL, weekends: weekends)
    }
    
    // MARK: - Load User RSVPs
    
    /// Loads the current user's RSVPs
    @MainActor
    func loadUserRSVPs() async {
        guard !currentUserID.isEmpty else { return }
        
        do {
            let snapshot = try await db.collection("eventRSVPs")
                .whereField("userID", isEqualTo: currentUserID)
                .getDocuments()
            
            var rsvps: [EventRSVP] = []
            
            for document in snapshot.documents {
                if let rsvp = parseEventRSVP(id: document.documentID, data: document.data()) {
                    rsvps.append(rsvp)
                }
            }
            
            userRSVPs = rsvps
            
        } catch {
            errorMessage = "Failed to load RSVPs: \(error.localizedDescription)"
        }
    }
    
    /// Parses a Firestore RSVP document
    private func parseEventRSVP(id: String, data: [String: Any]) -> EventRSVP? {
        guard
            let userID = data["userID"] as? String,
            let eventID = data["eventID"] as? String,
            let weekends = data["weekends"] as? [Int]
        else {
            return nil
        }
        
        let createdAt = (data["createdAt"] as? Timestamp)?.dateValue() ?? Date()
        
        return EventRSVP(id: id, userID: userID, eventID: eventID, weekends: weekends, createdAt: createdAt)
    }
    
    // MARK: - RSVP Management
    
    /// Adds or updates an RSVP for a specific event
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - weekends: The weekend numbers to RSVP to (e.g., [1], [2], or [1, 2])
    @MainActor
    func addOrUpdateRSVP(eventID: String, weekends: [Int]) async {
        guard !currentUserID.isEmpty else { return }
        guard !weekends.isEmpty else { return }
        
        do {
            // Check if user already has an RSVP for this event
            if let existingRSVP = userRSVPs.first(where: { $0.eventID == eventID }) {
                // Update existing RSVP
                try await db.collection("eventRSVPs")
                    .document(existingRSVP.id)
                    .updateData([
                        "weekends": weekends
                    ])
            } else {
                // Create new RSVP
                let newRSVP = EventRSVP(
                    userID: currentUserID,
                    eventID: eventID,
                    weekends: weekends
                )
                
                try await db.collection("eventRSVPs")
                    .document(newRSVP.id)
                    .setData([
                        "userID": newRSVP.userID,
                        "eventID": newRSVP.eventID,
                        "weekends": newRSVP.weekends,
                        "createdAt": Timestamp(date: newRSVP.createdAt)
                    ])
            }
            
            // Reload RSVPs
            await loadUserRSVPs()
            
        } catch {
            errorMessage = "Failed to RSVP: \(error.localizedDescription)"
        }
    }
    
    /// Cancels RSVP for a specific weekend
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - weekend: The weekend number to cancel (1 or 2)
    @MainActor
    func cancelRSVP(eventID: String, weekend: Int) async {
        guard !currentUserID.isEmpty else { return }
        guard let existingRSVP = userRSVPs.first(where: { $0.eventID == eventID }) else { return }
        
        do {
            let updatedWeekends = existingRSVP.weekends.filter { $0 != weekend }
            
            if updatedWeekends.isEmpty {
                // Delete the entire RSVP if no weekends left
                try await db.collection("eventRSVPs")
                    .document(existingRSVP.id)
                    .delete()
            } else {
                // Update with remaining weekends
                try await db.collection("eventRSVPs")
                    .document(existingRSVP.id)
                    .updateData([
                        "weekends": updatedWeekends
                    ])
            }
            
            // Reload RSVPs
            await loadUserRSVPs()
            
        } catch {
            errorMessage = "Failed to cancel RSVP: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Load Attendees
    
    /// Loads attendees for a specific event, filtered to weekends the current user RSVPed to
    /// Excludes the current user from the list
    /// - Parameter eventID: The event ID
    @MainActor
    func loadAttendees(for eventID: String) async {
        guard !currentUserID.isEmpty else { return }
        
        // Get user's RSVP
        guard let userRSVP = userRSVPs.first(where: { $0.eventID == eventID }),
              !userRSVP.weekends.isEmpty else {
            attendees = []
            return
        }
        
        do {
            // Fetch all RSVPs for this event
            let snapshot = try await db.collection("eventRSVPs")
                .whereField("eventID", isEqualTo: eventID)
                .getDocuments()
            
            var attendeeIDs: Set<String> = []
            
            for document in snapshot.documents {
                if let rsvp = parseEventRSVP(id: document.documentID, data: document.data()) {
                    // Only include attendees who RSVPed to at least one of the same weekends
                    let hasOverlap = rsvp.weekends.contains(where: { userRSVP.weekends.contains($0) })
                    
                    if hasOverlap && rsvp.userID != currentUserID {
                        attendeeIDs.insert(rsvp.userID)
                    }
                }
            }
            
            // Fetch user profiles for attendees
            var loadedAttendees: [FirebaseUser] = []
            
            for userID in attendeeIDs {
                if let user = try await firestoreService.fetchUser(userID: userID) {
                    loadedAttendees.append(user)
                }
            }
            
            attendees = loadedAttendees
            
        } catch {
            errorMessage = "Failed to load attendees: \(error.localizedDescription)"
        }
    }
    
    // MARK: - Helper Methods
    
    /// Gets the user's RSVP for a specific event
    /// - Parameter eventID: The event ID
    /// - Returns: The user's RSVP if it exists
    func getRSVP(for eventID: String) -> EventRSVP? {
        return userRSVPs.first(where: { $0.eventID == eventID })
    }
    
    /// Checks if user has RSVPed to a specific weekend
    /// - Parameters:
    ///   - eventID: The event ID
    ///   - weekend: The weekend number (1 or 2)
    /// - Returns: True if user has RSVPed to this weekend
    func hasRSVPed(eventID: String, weekend: Int) -> Bool {
        guard let rsvp = getRSVP(for: eventID) else { return false }
        return rsvp.weekends.contains(weekend)
    }
    
    /// Clears error message
    func clearError() {
        errorMessage = nil
    }
}
