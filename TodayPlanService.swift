//
//  TodayPlanService.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore
import FirebaseAuth

/// Firestore service for TodayPlan documents.
/// Collection: "todayPlans"
/// Required composite index: status ASC + scheduledTime ASC
/// (Firestore will print a direct URL to create it on first query run if missing.)
final class TodayPlanService {

    private let db = Firestore.firestore()
    private let collection = "todayPlans"
    private let matchesCollection = "matches"
    private let plansCollection = "plans"
    private let usersCollection = "users"

    static let shared = TodayPlanService()
    private init() {}

    // MARK: - Create

    @discardableResult
    func createTodayPlan(_ plan: TodayPlan) async throws -> TodayPlan {
        guard Auth.auth().currentUser != nil else {
            throw serviceError("User must be authenticated")
        }
        try await db.collection(collection).document(plan.id).setData(encode(plan))
        return plan
    }

    // MARK: - Fetch Open Plans

    /// Fetches open, non-expired plans for today — both filters applied server-side.
    /// Requires composite index: status ASC + scheduledTime ASC.
    func fetchOpenPlans() async throws -> [TodayPlan] {
        let (_, end) = todayBounds()
        let snapshot = try await db.collection(collection)
            .whereField("status", isEqualTo: TodayPlanStatus.open.rawValue)
            .whereField("scheduledTime", isGreaterThan: Timestamp(date: Date()))
            .whereField("scheduledTime", isLessThanOrEqualTo: Timestamp(date: end))
            .order(by: "scheduledTime", descending: false)
            .getDocuments()
        return snapshot.documents.compactMap { decode(id: $0.documentID, data: $0.data()) }
    }

    // MARK: - Real-time Listener

    /// Listens to open plans for today's calendar date.
    /// Uses start-of-day as the lower bound so the listener stays stable as time passes;
    /// TodayViewModel.filteredPlans trims any entries that expire between snapshots.
    /// Requires composite index: status ASC + scheduledTime ASC.
    func listenToOpenPlans(completion: @escaping ([TodayPlan]) -> Void) -> ListenerRegistration {
        let (start, end) = todayBounds()
        return db.collection(collection)
            .whereField("status", isEqualTo: TodayPlanStatus.open.rawValue)
            .whereField("scheduledTime", isGreaterThan: Timestamp(date: start))
            .whereField("scheduledTime", isLessThanOrEqualTo: Timestamp(date: end))
            .order(by: "scheduledTime", descending: false)
            .addSnapshotListener { snapshot, error in
                if let error {
                    print("❌ TodayPlanService listener: \(error.localizedDescription)")
                    return
                }
                let plans = snapshot?.documents.compactMap {
                    self.decode(id: $0.documentID, data: $0.data())
                } ?? []
                completion(plans)
            }
    }

    // MARK: - Claim

    /// Atomically marks a plan claimed, creates a mutual Match so MessagingViewModel
    /// surfaces a chat thread, and writes a pre-confirmed Plan document so the
    /// pinned plan card appears immediately when the thread opens.
    /// Returns the new matchID so callers can navigate directly to the thread.
    /// Throws with a user-visible message if the plan was already claimed (race condition).
    @discardableResult
    func claimTodayPlan(
        planID: String,
        claimerID: String,
        creatorID: String,
        activity: Activity,
        scheduledTime: Date
    ) async throws -> String {
        let planRef = db.collection(collection).document(planID)

        _ = try await db.runTransaction { transaction, errorPointer in
            let doc: DocumentSnapshot
            do { doc = try transaction.getDocument(planRef) }
            catch let err as NSError { errorPointer?.pointee = err; return nil }

            guard
                let status = doc.data()?["status"] as? String,
                status == TodayPlanStatus.open.rawValue
            else {
                errorPointer?.pointee = self.serviceError(
                    "This plan is no longer available — someone got there first!",
                    code: 409
                )
                return nil
            }
            transaction.updateData([
                "status": TodayPlanStatus.claimed.rawValue,
                "claimerID": claimerID,
                "updatedAt": Timestamp(date: Date())
            ], forDocument: planRef)
            return nil
        }

        // Find or create a mutual Match.
        // Query by user pair so this works with both legacy UUID docs and the new
        // deterministic "<minUID>_<maxUID>" scheme. user1ID is always the smaller UID.
        let u1 = min(creatorID, claimerID)
        let u2 = max(creatorID, claimerID)
        let now = Date()

        let existingSnap = try await db.collection(matchesCollection)
            .whereField("user1ID", isEqualTo: u1)
            .whereField("user2ID", isEqualTo: u2)
            .limit(to: 1)
            .getDocuments()

        let matchID: String
        if let existingDoc = existingSnap.documents.first {
            // A match already exists for this pair — upgrade it to mutual in-place.
            matchID = existingDoc.documentID
            try await db.collection(matchesCollection).document(matchID).updateData([
                "user1Decision": true,
                "user2Decision": true,
                "isMutualMatch": true,
                "updatedAt": Timestamp(date: now)
            ])
        } else {
            matchID = "\(u1)_\(u2)"
            try await db.collection(matchesCollection).document(matchID).setData([
                "user1ID": u1,
                "user2ID": u2,
                "user1Decision": true,
                "user2Decision": true,
                "isMutualMatch": true,
                "overlappingActivityNames": [activity.name],
                "overlappingDaySlots": [] as [String],
                "createdAt": Timestamp(date: now),
                "updatedAt": Timestamp(date: now)
            ])
        }

        // Write a pre-confirmed Plan so listenToConfirmedPlan picks it up immediately
        // and populates the pinned plan card in the message thread.
        // claimerID is request.auth.uid at write time, so it must be proposerID
        // to satisfy the plans 'allow create' rule (proposerID == request.auth.uid).
        let activityData = (try? Firestore.Encoder().encode(activity)) ?? [:]
        try await db.collection(plansCollection).document(UUID().uuidString).setData([
            "matchID": matchID,
            "proposerID": claimerID,
            "receiverID": creatorID,
            "activity": activityData,
            "proposedDates": [Timestamp(date: scheduledTime)],
            "status": PlanStatus.confirmed.rawValue,
            "confirmedDate": Timestamp(date: scheduledTime),
            "createdAt": Timestamp(date: now),
            "updatedAt": Timestamp(date: now)
        ])

        return matchID
    }

    // MARK: - Show-up Report

    /// Writes a show-up report to the showUpReports collection.
    /// The applyShowUpReport Cloud Function handles plan doc updates and user counter
    /// increments via Admin SDK, keeping all cross-user writes server-side.
    func submitShowUpReport(
        planID: String,
        reporterID: String,
        didShowUp: Bool,
        reportedUserID: String
    ) async throws {
        try await db.collection("showUpReports").addDocument(data: [
            "reporterID": reporterID,
            "reportedUserID": reportedUserID,
            "planID": planID,
            "didShowUp": didShowUp,
            "createdAt": Timestamp(date: Date())
        ])
    }

    // MARK: - Show-Up Report Lookup

    /// Returns the most-recent claimed TodayPlan for this user pair that still
    /// needs a show-up report from currentUserID, or nil if none is pending.
    /// Two equality-filter queries cover both creator/claimer orderings — same
    /// pattern as FirestoreService.createMatch (no composite index required).
    func fetchPendingShowUpReport(
        currentUserID: String,
        otherUserID: String
    ) async throws -> TodayPlan? {
        let snap1 = try await db.collection(collection)
            .whereField("creatorID", isEqualTo: currentUserID)
            .whereField("claimerID", isEqualTo: otherUserID)
            .getDocuments()

        let snap2 = try await db.collection(collection)
            .whereField("creatorID", isEqualTo: otherUserID)
            .whereField("claimerID", isEqualTo: currentUserID)
            .getDocuments()

        return (snap1.documents + snap2.documents)
            .compactMap { decode(id: $0.documentID, data: $0.data()) }
            .first { $0.awaitingReport(from: currentUserID) }
    }

    // MARK: - Serialization

    private func encode(_ plan: TodayPlan) -> [String: Any] {
        var data: [String: Any] = [
            "creatorID": plan.creatorID,
            "activity": (try? Firestore.Encoder().encode(plan.activity)) ?? [:],
            "scheduledTime": Timestamp(date: plan.scheduledTime),
            "status": plan.status.rawValue,
            "createdAt": Timestamp(date: plan.createdAt),
            "updatedAt": Timestamp(date: plan.updatedAt)
        ]
        if let note = plan.note { data["note"] = note }
        if let v = plan.location { data["location"] = v }
        if let v = plan.locationName { data["locationName"] = v }
        if let v = plan.locationLatitude { data["locationLatitude"] = v }
        if let v = plan.locationLongitude { data["locationLongitude"] = v }
        if let v = plan.claimerID { data["claimerID"] = v }
        if let v = plan.creatorReportedClaimer { data["creatorReportedClaimer"] = v }
        if let v = plan.claimerReportedCreator { data["claimerReportedCreator"] = v }
        return data
    }

    private func decode(id: String, data: [String: Any]) -> TodayPlan? {
        guard
            let creatorID = data["creatorID"] as? String,
            let actData = data["activity"] as? [String: Any],
            let activity = try? Firestore.Decoder().decode(Activity.self, from: actData),
            let scheduledTS = data["scheduledTime"] as? Timestamp,
            let statusRaw = data["status"] as? String,
            let status = TodayPlanStatus(rawValue: statusRaw),
            let createdTS = data["createdAt"] as? Timestamp,
            let updatedTS = data["updatedAt"] as? Timestamp
        else { return nil }

        return TodayPlan(
            id: id,
            creatorID: creatorID,
            activity: activity,
            scheduledTime: scheduledTS.dateValue(),
            note: data["note"] as? String,
            location: data["location"] as? String,
            locationName: data["locationName"] as? String,
            locationLatitude: data["locationLatitude"] as? Double,
            locationLongitude: data["locationLongitude"] as? Double,
            status: status,
            claimerID: data["claimerID"] as? String,
            createdAt: createdTS.dateValue(),
            updatedAt: updatedTS.dateValue(),
            creatorReportedClaimer: data["creatorReportedClaimer"] as? Bool,
            claimerReportedCreator: data["claimerReportedClaimer"] as? Bool
        )
    }

    // MARK: - Helpers

    private func todayBounds() -> (start: Date, end: Date) {
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        let end = cal.date(byAdding: .day, value: 1, to: start)!.addingTimeInterval(-1)
        return (start, end)
    }

    private func serviceError(_ message: String, code: Int = -1) -> NSError {
        NSError(domain: "TodayPlanService", code: code, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
