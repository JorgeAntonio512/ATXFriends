//
//  SimpaticoSharedVectorsTests.swift
//  Avenue3
//
//  Scores every case in the repo-root shared-test-vectors/simpatico.json with the real
//  SimpaticoV2State.score(between:and:). The Android app checks the same file
//  (SimpaticoSharedVectorsTest.kt), so the Simpatico % is identical on both platforms.
//

import Foundation
import Testing
@testable import ATX_Friends

struct SimpaticoSharedVectorsTests {

    private struct VectorFile: Decodable {
        let cases: [VectorCase]
    }

    private struct VectorCase: Decodable {
        let name: String
        let a: [String: SimpaticoV2Answer]
        let b: [String: SimpaticoV2Answer]
        /// nil means no score (fewer than 5 shared questions).
        let expected: Int?
    }

    private static func loadCases() throws -> [VectorCase] {
        // <repo>/Avenue3Tests/SimpaticoSharedVectorsTests.swift → <repo>/shared-test-vectors/simpatico.json
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("shared-test-vectors/simpatico.json")
        return try JSONDecoder().decode(VectorFile.self, from: Data(contentsOf: url)).cases
    }

    @Test func everySharedCaseScoresExactlyAsExpected() throws {
        let cases = try Self.loadCases()
        #expect(cases.count >= 10)

        for vector in cases {
            let score = SimpaticoV2State.score(
                between: SimpaticoV2State(userID: "a", answers: vector.a),
                and: SimpaticoV2State(userID: "b", answers: vector.b)
            )
            #expect(score == vector.expected, "\(vector.name): expected \(String(describing: vector.expected)), got \(String(describing: score))")
        }
    }
}
