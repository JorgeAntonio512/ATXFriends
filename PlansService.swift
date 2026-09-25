//
//  PlansService.swift
//  Avenue3
//
//  Created by George Anthony Pazdral II on 5/17/26.
//

import Foundation
import FirebaseFirestore
import FirebaseAuth

/// Service for managing plans (proposed hangouts) in Firestore
final class PlansService {
    // MARK: - Properties
    
    private let db = Firestore.firestore()
    private let plansCollection = "plans"
    
    // MARK: - Singleton
    
    static let shared = PlansService()
    
    private init() {}
    
    // MARK: - Create Plan
    
    /// Creates a new plan proposal
    @discardableResult
    func createPlan(_ plan: Plan) async throws -> Plan {
        print("📅 DEBUG: Creating plan...")
        print("📅 DEBUG: Plan ID: \(plan.id)")
        print("📅 DEBUG: Match ID: \(plan.matchID)")
        print("📅 DEBUG: Activity: \(plan.activity.name)")
        
        // Check authentication
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: Cannot create plan - user not authenticated")
            throw NSError(
                domain: "PlansService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("✅ DEBUG: User authenticated: \(currentUser.uid)")
        
        let planData = planToFirestoreData(plan)
        
        try await db.collection(plansCollection)
            .document(plan.id)
            .setData(planData)
        
        print("✅ DEBUG: Plan created successfully!")
        return plan
    }
    
    // MARK: - Fetch Plans
    
    /// Fetches all plans for a specific user
    func fetchPlans(for userID: String) async throws -> [Plan] {
        print("📅 DEBUG: Fetching plans for user: \(userID)")
        
        // Fetch plans where user is either proposer or receiver
        let proposerSnapshot = try await db.collection(plansCollection)
            .whereField("proposerID", isEqualTo: userID)
            .getDocuments()
        
        let receiverSnapshot = try await db.collection(plansCollection)
            .whereField("receiverID", isEqualTo: userID)
            .getDocuments()
        
        var plans: [Plan] = []
        
        for document in proposerSnapshot.documents {
            if let plan = firestoreDataToPlan(id: document.documentID, data: document.data()) {
                plans.append(plan)
            }
        }
        
        for document in receiverSnapshot.documents {
            if let plan = firestoreDataToPlan(id: document.documentID, data: document.data()) {
                plans.append(plan)
            }
        }
        
        print("✅ DEBUG: Fetched \(plans.count) plans")
        return plans
    }
    
    /// Fetches a single plan by document ID
    func fetchPlan(planID: String) async throws -> Plan? {
        let document = try await db.collection(plansCollection).document(planID).getDocument()
        guard document.exists, let data = document.data() else { return nil }
        return firestoreDataToPlan(id: document.documentID, data: data)
    }

    // MARK: - Confirmed Plan Listener

    /// Real-time listener that delivers all confirmed, unexpired upcoming plans for a match,
    /// sorted soonest first, plus the full set of confirmed plan IDs for that match (including
    /// past ones) so callers can tell whether any given plan is confirmed without a second query.
    /// "Confirmed" includes plans with a pending reschedule request (status counter) — the
    /// original time still stands until the other person accepts.
    /// Delivers empty results when none exist. Filters by matchID only (no composite index
    /// needed) then narrows client-side.
    func listenToConfirmedPlan(
        forMatch matchID: String,
        completion: @escaping (_ upcomingConfirmedPlans: [Plan], _ allConfirmedPlanIDs: Set<String>) -> Void
    ) -> ListenerRegistration {
        db.collection(plansCollection)
            .whereField("matchID", isEqualTo: matchID)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    print("❌ PlansService.listenToConfirmedPlan error: \(error.localizedDescription)")
                    completion([], [])
                    return
                }
                guard let self else { completion([], []); return }
                let plans = snapshot?.documents
                    .compactMap { self.firestoreDataToPlan(id: $0.documentID, data: $0.data()) } ?? []
                print("📌 PlansService.listenToConfirmedPlan: \(plans.count) total plans for match \(matchID)")
                let confirmedPlans = plans.filter { $0.isConfirmedOrReschedulePending }
                let upcoming = confirmedPlans
                    .filter { $0.isUpcoming() }
                    .sorted { ($0.standingDate ?? .distantFuture) < ($1.standingDate ?? .distantFuture) }
                let allConfirmedIDs = Set(confirmedPlans.map(\.id))
                print("📌 PlansService.listenToConfirmedPlan: \(upcoming.count) confirmed upcoming plan(s), \(allConfirmedIDs.count) confirmed total")
                completion(upcoming, allConfirmedIDs)
            }
    }

    /// Fetches plans for a specific match
    func fetchPlans(forMatch matchID: String) async throws -> [Plan] {
        let snapshot = try await db.collection(plansCollection)
            .whereField("matchID", isEqualTo: matchID)
            .order(by: "createdAt", descending: true)
            .getDocuments()
        
        var plans: [Plan] = []
        
        for document in snapshot.documents {
            if let plan = firestoreDataToPlan(id: document.documentID, data: document.data()) {
                plans.append(plan)
            }
        }
        
        return plans
    }
    
    // MARK: - Update Plan
    
    /// Updates a plan (for status changes, confirmations, etc.)
    func updatePlan(_ plan: Plan) async throws {
        print("📅 DEBUG: Updating plan: \(plan.id)")
        print("📅 DEBUG: New status: \(plan.status.rawValue)")
        
        // Check authentication
        guard let currentUser = Auth.auth().currentUser else {
            print("❌ DEBUG: Cannot update plan - user not authenticated")
            throw NSError(
                domain: "PlansService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        
        print("✅ DEBUG: User authenticated: \(currentUser.uid)")
        
        var updatedPlan = plan
        updatedPlan.updatedAt = Date()
        
        let planData = planToFirestoreData(updatedPlan)
        
        try await db.collection(plansCollection)
            .document(plan.id)
            .setData(planData)
        
        print("✅ DEBUG: Plan updated successfully!")
    }
    
    /// Confirms a plan with a selected date
    func confirmPlan(planID: String, selectedDate: Date) async throws {
        try await db.collection(plansCollection)
            .document(planID)
            .updateData([
                "status": PlanStatus.confirmed.rawValue,
                "confirmedDate": Timestamp(date: selectedDate),
                "updatedAt": Timestamp(date: Date())
            ])
    }
    
    /// Requests a reschedule of a confirmed plan. The original confirmedDate is left intact
    /// until the other person accepts; counterProposedBy records who asked.
    func counterPropose(planID: String, newDates: [Date]) async throws {
        guard let requesterID = Auth.auth().currentUser?.uid else {
            throw NSError(
                domain: "PlansService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        try await db.collection(plansCollection)
            .document(planID)
            .updateData([
                "status": PlanStatus.counterProposed.rawValue,
                "counterProposedDates": newDates.map { Timestamp(date: $0) },
                "counterProposedBy": requesterID,
                "updatedAt": Timestamp(date: Date())
            ])
    }

    /// Accepts a pending reschedule: the plan is confirmed at the suggested time and the
    /// request is cleared.
    func acceptReschedule(_ plan: Plan) async throws {
        guard let newDate = plan.pendingRescheduleDate else { return }
        try await db.collection(plansCollection)
            .document(plan.id)
            .updateData([
                "status": PlanStatus.confirmed.rawValue,
                "confirmedDate": Timestamp(date: newDate),
                "counterProposedDates": FieldValue.delete(),
                "counterProposedBy": FieldValue.delete(),
                "updatedAt": Timestamp(date: Date())
            ])
    }

    /// Declines a pending reschedule: the plan goes back to confirmed at its original time
    /// and the request is cleared. Never cancels the plan.
    func declineReschedule(_ plan: Plan) async throws {
        var data: [String: Any] = [
            "status": PlanStatus.confirmed.rawValue,
            "counterProposedDates": FieldValue.delete(),
            "counterProposedBy": FieldValue.delete(),
            "updatedAt": Timestamp(date: Date())
        ]
        // Legacy counter plans may never have had a confirmedDate; restore the original
        // proposed time so the plan comes back confirmed with a date.
        if plan.confirmedDate == nil, let original = plan.proposedDates.first {
            data["confirmedDate"] = Timestamp(date: original)
        }
        try await db.collection(plansCollection)
            .document(plan.id)
            .updateData(data)
    }
    
    /// Declines a plan
    func declinePlan(planID: String) async throws {
        try await db.collection(plansCollection)
            .document(planID)
            .updateData([
                "status": PlanStatus.declined.rawValue,
                "updatedAt": Timestamp(date: Date())
            ])
    }
    
    /// Cancels a plan
    func cancelPlan(planID: String) async throws {
        try await db.collection(plansCollection)
            .document(planID)
            .updateData([
                "status": PlanStatus.cancelled.rawValue,
                "updatedAt": Timestamp(date: Date())
            ])
    }
    
    // MARK: - Real-time Listener
    
    /// Sets up a real-time listener for plans (both as proposer and receiver)
    /// Note: Firestore doesn't support OR queries, so we need to use two listeners
    func listenToPlans(
        for userID: String,
        completion: @escaping ([Plan]) -> Void
    ) -> ListenerRegistration {
        print("📅 DEBUG: Setting up plan listener for user: \(userID)")
        
        var receiverPlans: [String: Plan] = [:]
        var proposerPlans: [String: Plan] = [:]
        
        let mergeAndNotify = {
            // Merge both sets of plans, using dictionary to avoid duplicates
            var allPlans = receiverPlans
            for (id, plan) in proposerPlans {
                allPlans[id] = plan
            }
            let plans = Array(allPlans.values)
            print("✅ DEBUG: Listener received \(plans.count) total plans (\(receiverPlans.count) as receiver, \(proposerPlans.count) as proposer)")
            completion(plans)
        }
        
        // Listener 1: Plans where user is receiver
        let receiverListener = db.collection(plansCollection)
            .whereField("receiverID", isEqualTo: userID)
            .addSnapshotListener { snapshot, error in
                if let error = error {
                    print("❌ DEBUG: Error listening to receiver plans: \(error.localizedDescription)")
                    return
                }
                
                guard let snapshot = snapshot else {
                    print("❌ DEBUG: Receiver snapshot is nil")
                    return
                }
                
                receiverPlans.removeAll()
                for document in snapshot.documents {
                    if let plan = self.firestoreDataToPlan(id: document.documentID, data: document.data()) {
                        receiverPlans[plan.id] = plan
                    }
                }
                
                mergeAndNotify()
            }
        
        // Listener 2: Plans where user is proposer
        let proposerListener = db.collection(plansCollection)
            .whereField("proposerID", isEqualTo: userID)
            .addSnapshotListener { snapshot, error in
                if let error = error {
                    print("❌ DEBUG: Error listening to proposer plans: \(error.localizedDescription)")
                    return
                }
                
                guard let snapshot = snapshot else {
                    print("❌ DEBUG: Proposer snapshot is nil")
                    return
                }
                
                proposerPlans.removeAll()
                for document in snapshot.documents {
                    if let plan = self.firestoreDataToPlan(id: document.documentID, data: document.data()) {
                        proposerPlans[plan.id] = plan
                    }
                }
                
                mergeAndNotify()
            }
        
        // Return a composite listener that removes both
        return CompositeListenerRegistration(listeners: [receiverListener, proposerListener])
    }
    
    // MARK: - Data Conversion
    
    /// Converts a Plan to Firestore data
    private func planToFirestoreData(_ plan: Plan) -> [String: Any] {
        var data: [String: Any] = [
            "matchID": plan.matchID,
            "proposerID": plan.proposerID,
            "receiverID": plan.receiverID,
            "activity": try! Firestore.Encoder().encode(plan.activity),
            "proposedDates": plan.proposedDates.map { Timestamp(date: $0) },
            "status": plan.status.rawValue,
            "createdAt": Timestamp(date: plan.createdAt),
            "updatedAt": Timestamp(date: plan.updatedAt)
        ]
        
        if let location = plan.location {
            data["location"] = location
        }

        if let locationName = plan.locationName {
            data["locationName"] = locationName
        }

        if let locationLatitude = plan.locationLatitude {
            data["locationLatitude"] = locationLatitude
        }

        if let locationLongitude = plan.locationLongitude {
            data["locationLongitude"] = locationLongitude
        }

        if let confirmedDate = plan.confirmedDate {
            data["confirmedDate"] = Timestamp(date: confirmedDate)
        }
        
        if let counterDates = plan.counterProposedDates {
            data["counterProposedDates"] = counterDates.map { Timestamp(date: $0) }
        }

        if let counterProposedBy = plan.counterProposedBy {
            data["counterProposedBy"] = counterProposedBy
        }
        
        return data
    }
    
    /// Converts Firestore data to a Plan
    private func firestoreDataToPlan(id: String, data: [String: Any]) -> Plan? {
        guard
            let matchID = data["matchID"] as? String,
            let proposerID = data["proposerID"] as? String,
            let receiverID = data["receiverID"] as? String,
            let activityData = data["activity"] as? [String: Any],
            let proposedDatesTimestamps = data["proposedDates"] as? [Timestamp],
            let statusRaw = data["status"] as? String,
            let status = PlanStatus(rawValue: statusRaw),
            let createdAtTimestamp = data["createdAt"] as? Timestamp,
            let updatedAtTimestamp = data["updatedAt"] as? Timestamp
        else {
            print("❌ DEBUG: Failed to parse plan data")
            return nil
        }
        
        // Parse activity
        guard let activity = try? Firestore.Decoder().decode(Activity.self, from: activityData) else {
            print("❌ DEBUG: Failed to parse activity")
            return nil
        }
        
        let location = data["location"] as? String
        let locationName = data["locationName"] as? String
        let locationLatitude = data["locationLatitude"] as? Double
        let locationLongitude = data["locationLongitude"] as? Double
        let proposedDates = proposedDatesTimestamps.map { $0.dateValue() }
        
        let confirmedDate: Date?
        if let confirmedTimestamp = data["confirmedDate"] as? Timestamp {
            confirmedDate = confirmedTimestamp.dateValue()
        } else {
            confirmedDate = nil
        }
        
        let counterProposedDates: [Date]?
        if let counterTimestamps = data["counterProposedDates"] as? [Timestamp] {
            counterProposedDates = counterTimestamps.map { $0.dateValue() }
        } else {
            counterProposedDates = nil
        }
        let counterProposedBy = data["counterProposedBy"] as? String
        
        return Plan(
            id: id,
            matchID: matchID,
            proposerID: proposerID,
            receiverID: receiverID,
            activity: activity,
            location: location,
            locationName: locationName,
            locationLatitude: locationLatitude,
            locationLongitude: locationLongitude,
            proposedDates: proposedDates,
            status: status,
            confirmedDate: confirmedDate,
            createdAt: createdAtTimestamp.dateValue(),
            updatedAt: updatedAtTimestamp.dateValue(),
            counterProposedDates: counterProposedDates,
            counterProposedBy: counterProposedBy
        )
    }
}

// MARK: - Helper Classes

/// A composite listener registration that manages multiple Firestore listeners
final class CompositeListenerRegistration: NSObject, ListenerRegistration {
    private let listeners: [ListenerRegistration]
    
    init(listeners: [ListenerRegistration]) {
        self.listeners = listeners
        super.init()
    }
    
    func remove() {
        listeners.forEach { $0.remove() }
    }
}
