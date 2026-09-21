//
//  GroupPlansService.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore
import FirebaseAuth

/// Service for managing group plans (multi-invitee hangouts) in Firestore.
final class GroupPlansService {
    private let db = Firestore.firestore()
    private let collection = "groupPlans"

    static let shared = GroupPlansService()

    private init() {}

    // MARK: - Create

    @discardableResult
    func createGroupPlan(_ plan: GroupPlan) async throws -> GroupPlan {
        guard Auth.auth().currentUser != nil else {
            throw NSError(
                domain: "GroupPlansService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "User must be authenticated"]
            )
        }
        do {
            try await db.collection(collection).document(plan.id).setData(groupPlanToFirestoreData(plan))
        } catch {
            let nsError = error as NSError
            print("[GroupPlans] createGroupPlan failed: domain=\(nsError.domain) code=\(nsError.code) message=\(nsError.localizedDescription)")
            throw error
        }
        return plan
    }

    // MARK: - Fetch

    func fetchGroupPlan(planID: String) async throws -> GroupPlan? {
        let document = try await db.collection(collection).document(planID).getDocument()
        guard document.exists, let data = document.data() else { return nil }
        return firestoreDataToGroupPlan(id: document.documentID, data: data)
    }

    // MARK: - Real-time Listener

    /// Listens for every active-or-not group plan where the user is host or invitee.
    /// Firestore doesn't support OR queries, so two listeners are merged, mirroring
    /// PlansService.listenToPlans.
    func listenToGroupPlans(
        for userID: String,
        completion: @escaping ([GroupPlan]) -> Void
    ) -> ListenerRegistration {
        var hostPlans: [String: GroupPlan] = [:]
        var inviteePlans: [String: GroupPlan] = [:]

        let mergeAndNotify = {
            var all = inviteePlans
            for (id, plan) in hostPlans { all[id] = plan }
            completion(Array(all.values))
        }

        let hostListener = db.collection(collection)
            .whereField("hostID", isEqualTo: userID)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    let nsError = error as NSError
                    print("[GroupPlans] host listener failed: domain=\(nsError.domain) code=\(nsError.code) message=\(nsError.localizedDescription)")
                }
                guard let self, error == nil, let snapshot else { return }
                hostPlans.removeAll()
                for document in snapshot.documents {
                    if let plan = self.firestoreDataToGroupPlan(id: document.documentID, data: document.data()) {
                        hostPlans[plan.id] = plan
                    }
                }
                mergeAndNotify()
            }

        let inviteeListener = db.collection(collection)
            .whereField("inviteeIDs", arrayContains: userID)
            .addSnapshotListener { [weak self] snapshot, error in
                if let error {
                    let nsError = error as NSError
                    print("[GroupPlans] invitee listener failed: domain=\(nsError.domain) code=\(nsError.code) message=\(nsError.localizedDescription)")
                }
                guard let self, error == nil, let snapshot else { return }
                inviteePlans.removeAll()
                for document in snapshot.documents {
                    if let plan = self.firestoreDataToGroupPlan(id: document.documentID, data: document.data()) {
                        inviteePlans[plan.id] = plan
                    }
                }
                mergeAndNotify()
            }

        return CompositeListenerRegistration(listeners: [hostListener, inviteeListener])
    }

    // MARK: - Update

    /// Sets a single invitee's response. Writes only the `responses.<uid>` field, matching
    /// the security rule that lets an invitee touch only their own response key.
    func setResponse(planID: String, userID: String, response: GroupPlanResponse) async throws {
        try await db.collection(collection).document(planID).updateData([
            "responses.\(userID)": response.rawValue,
            "updatedAt": Timestamp(date: Date())
        ])
    }

    /// Cancels a plan. Host-only, enforced by security rules.
    func cancelPlan(planID: String) async throws {
        try await db.collection(collection).document(planID).updateData([
            "status": GroupPlanStatus.cancelled.rawValue,
            "updatedAt": Timestamp(date: Date())
        ])
    }

    // MARK: - Data Conversion

    private func groupPlanToFirestoreData(_ plan: GroupPlan) -> [String: Any] {
        var data: [String: Any] = [
            "hostID": plan.hostID,
            "inviteeIDs": plan.inviteeIDs,
            "responses": Dictionary(uniqueKeysWithValues: plan.responses.map { ($0.key, $0.value.rawValue) }),
            "activity": try! Firestore.Encoder().encode(plan.activity),
            "date": Timestamp(date: plan.date),
            "status": plan.status.rawValue,
            "createdAt": Timestamp(date: plan.createdAt),
            "updatedAt": Timestamp(date: plan.updatedAt)
        ]

        if let location = plan.location { data["location"] = location }
        if let locationName = plan.locationName { data["locationName"] = locationName }
        if let locationLatitude = plan.locationLatitude { data["locationLatitude"] = locationLatitude }
        if let locationLongitude = plan.locationLongitude { data["locationLongitude"] = locationLongitude }

        return data
    }

    private func firestoreDataToGroupPlan(id: String, data: [String: Any]) -> GroupPlan? {
        guard
            let hostID = data["hostID"] as? String,
            let inviteeIDs = data["inviteeIDs"] as? [String],
            let responsesRaw = data["responses"] as? [String: String],
            let activityData = data["activity"] as? [String: Any],
            let activity = try? Firestore.Decoder().decode(Activity.self, from: activityData),
            let dateTimestamp = data["date"] as? Timestamp,
            let statusRaw = data["status"] as? String,
            let status = GroupPlanStatus(rawValue: statusRaw),
            let createdAtTimestamp = data["createdAt"] as? Timestamp,
            let updatedAtTimestamp = data["updatedAt"] as? Timestamp
        else {
            print("❌ DEBUG: Failed to parse group plan data")
            return nil
        }

        let responses = responsesRaw.compactMapValues { GroupPlanResponse(rawValue: $0) }

        return GroupPlan(
            id: id,
            hostID: hostID,
            inviteeIDs: inviteeIDs,
            responses: responses,
            activity: activity,
            location: data["location"] as? String,
            locationName: data["locationName"] as? String,
            locationLatitude: data["locationLatitude"] as? Double,
            locationLongitude: data["locationLongitude"] as? Double,
            date: dateTimestamp.dateValue(),
            status: status,
            createdAt: createdAtTimestamp.dateValue(),
            updatedAt: updatedAtTimestamp.dateValue()
        )
    }
}
