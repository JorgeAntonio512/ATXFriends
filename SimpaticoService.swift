//
//  SimpaticoService.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore
import FirebaseAuth

/// Firestore service for Simpatico questionnaire documents.
/// Collection: "simpaticoAnswers" — one document per user, keyed by userID.
final class SimpaticoService {

    static let shared = SimpaticoService()
    private init() {}

    private let db = Firestore.firestore()
    private let collection = "simpaticoAnswers"

    // MARK: - Fetch

    /// Returns the user's saved questionnaire, or an empty one if they haven't started yet.
    func fetchQuestionnaire(userID: String) async throws -> SimpaticoQuestionnaire {
        let doc = try await db.collection(collection).document(userID).getDocument()
        guard doc.exists, let data = doc.data() else {
            return SimpaticoQuestionnaire(userID: userID)
        }
        return decode(userID: userID, data: data)
    }

    // MARK: - Save (partial)

    /// Writes a single answer without touching any other answers already saved.
    /// Creates the Firestore document on the first call; subsequent calls merge into it.
    ///
    /// Uses setData with mergeFields so:
    /// - The document is created if it does not yet exist (unlike updateData).
    /// - Only the targeted nested field and the timestamp are written (unlike setData + merge: true,
    ///   which would replace the entire "answers" map with the single entry passed in).
    func saveAnswer(_ answer: SimpaticoAnswer, for component: SimpaticoComponent, userID: String) async throws {
        let answerDict: [String: Any] = [
            "toYou": answer.toYou.rawValue,
            "toFriend": answer.toFriend.rawValue
        ]
        let data: [String: Any] = [
            "userID": userID,
            "updatedAt": Timestamp(date: Date()),
            "answers": [component.rawValue: answerDict]
        ]
        // FieldPath(["answers", component.rawValue]) targets the nested map entry
        // without clobbering sibling answer fields.
        try await db.collection(collection).document(userID).setData(
            data,
            mergeFields: [
                FieldPath(["userID"]),
                FieldPath(["updatedAt"]),
                FieldPath(["answers", component.rawValue])
            ]
        )
    }

    // MARK: - Score

    /// Fetches both questionnaires concurrently and returns the live-computed score,
    /// or nil if either user hasn't answered all 18 questions yet.
    /// Follows the same pattern as showUpMeter: raw data is stored, the number is
    /// derived on access rather than persisted.
    func fetchScore(userID: String, friendID: String) async throws -> Int? {
        async let q1 = fetchQuestionnaire(userID: userID)
        async let q2 = fetchQuestionnaire(userID: friendID)
        let (a, b) = try await (q1, q2)
        return SimpaticoQuestionnaire.score(between: a, and: b)
    }

    // MARK: - Decode

    private func decode(userID: String, data: [String: Any]) -> SimpaticoQuestionnaire {
        let updatedAt = (data["updatedAt"] as? Timestamp)?.dateValue() ?? Date()
        let rawAnswers = data["answers"] as? [String: [String: String]] ?? [:]

        var answers: [String: SimpaticoAnswer] = [:]
        for (key, dict) in rawAnswers {
            guard
                let toYouRaw   = dict["toYou"],
                let toFriendRaw = dict["toFriend"],
                let toYou      = SimpaticoRating(rawValue: toYouRaw),
                let toFriend   = SimpaticoRating(rawValue: toFriendRaw)
            else { continue }
            answers[key] = SimpaticoAnswer(toYou: toYou, toFriend: toFriend)
        }

        return SimpaticoQuestionnaire(userID: userID, answers: answers, updatedAt: updatedAt)
    }
}
