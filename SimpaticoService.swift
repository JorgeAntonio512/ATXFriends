//
//  SimpaticoService.swift
//  Avenue3
//

import Foundation
import FirebaseFirestore
import FirebaseAuth

/// Firestore service for Simpatico questionnaire documents.
/// Collection: "simpaticoAnswers" — one document per user, keyed by userID.
/// The legacy "answers" field (pre-v2) is left untouched: it is never read or written here.
final class SimpaticoService {

    static let shared = SimpaticoService()
    private init() {}

    private let db = Firestore.firestore()
    private let collection = "simpaticoAnswers"

    // MARK: - Fetch

    /// Returns the user's saved v2 state, or an empty one if they haven't started yet.
    func fetchState(userID: String) async throws -> SimpaticoV2State {
        let doc = try await db.collection(collection).document(userID).getDocument()
        guard doc.exists, let data = doc.data() else {
            return SimpaticoV2State(userID: userID)
        }
        return decode(userID: userID, data: data)
    }

    // MARK: - Save (partial)

    /// Writes a single v2 answer without touching any other answers already saved.
    /// Creates the Firestore document on the first call; subsequent calls merge into it.
    func saveV2Answer(_ answer: SimpaticoV2Answer, for questionID: String, userID: String) async throws {
        var answerDict: [String: Any] = [
            "answer": answer.answer,
            "acceptable": answer.acceptable
        ]
        if let importance = answer.importance {
            answerDict["importance"] = importance.rawValue
        }
        let data: [String: Any] = [
            "userID": userID,
            "v2Answers": [questionID: answerDict]
        ]
        // FieldPath(["v2Answers", questionID]) targets the nested map entry without
        // clobbering sibling answers or the legacy "answers" field.
        try await db.collection(collection).document(userID).setData(
            data,
            mergeFields: [
                FieldPath(["userID"]),
                FieldPath(["v2Answers", questionID])
            ]
        )
    }

    /// Marks the v2 flow finished (Finish tapped, or Skip past the last question).
    func markV2Complete(userID: String) async throws {
        try await db.collection(collection).document(userID).setData(
            ["v2CompletedAt": Timestamp(date: Date())],
            mergeFields: [FieldPath(["v2CompletedAt"])]
        )
    }

    // MARK: - Score

    /// Fetches both users' v2 state concurrently and returns the live-computed score,
    /// or nil if they share fewer than 5 answered questions.
    func fetchScore(userID: String, friendID: String) async throws -> Int? {
        async let s1 = fetchState(userID: userID)
        async let s2 = fetchState(userID: friendID)
        let (a, b) = try await (s1, s2)
        return SimpaticoV2State.score(between: a, and: b)
    }

    // MARK: - Decode

    private func decode(userID: String, data: [String: Any]) -> SimpaticoV2State {
        let hasLegacyAnswers = !(data["answers"] as? [String: Any] ?? [:]).isEmpty
        let completedAt = (data["v2CompletedAt"] as? Timestamp)?.dateValue()
        let rawV2Answers = data["v2Answers"] as? [String: [String: Any]] ?? [:]

        var answers: [String: SimpaticoV2Answer] = [:]
        for (questionID, dict) in rawV2Answers {
            guard
                let answerID = dict["answer"] as? String,
                let acceptable = dict["acceptable"] as? [String]
            else { continue }
            let importance = (dict["importance"] as? String).flatMap(SimpaticoImportance.init(rawValue:))
            answers[questionID] = SimpaticoV2Answer(answer: answerID, acceptable: acceptable, importance: importance)
        }

        return SimpaticoV2State(userID: userID, answers: answers, completedAt: completedAt, hasLegacyAnswers: hasLegacyAnswers)
    }
}
