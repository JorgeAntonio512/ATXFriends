//
//  SimpaticoModelsTests.swift
//  Avenue3
//

import Testing
@testable import Avenue3

struct SimpaticoModelsTests {

    // MARK: - Helpers

    private func optionID(_ questionID: String, _ index: Int) -> String {
        SimpaticoQuestionBank.question(id: questionID)!.options[index].id
    }

    private func allOptionIDs(_ questionID: String) -> [String] {
        SimpaticoQuestionBank.question(id: questionID)!.options.map(\.id)
    }

    private let fiveQuestionIDs = ["saturday", "hangSize", "planning", "bestTime", "socialBattery"]

    // MARK: - Fewer than 5 shared questions

    @Test func fewerThanFiveSharedReturnsNil() {
        var a: [String: SimpaticoV2Answer] = [:]
        var b: [String: SimpaticoV2Answer] = [:]
        for questionID in fiveQuestionIDs.prefix(4) {
            let id = optionID(questionID, 0)
            a[questionID] = SimpaticoV2Answer(answer: id, acceptable: [id], importance: .very)
            b[questionID] = SimpaticoV2Answer(answer: id, acceptable: [id], importance: .very)
        }
        let stateA = SimpaticoV2State(userID: "a", answers: a)
        let stateB = SimpaticoV2State(userID: "b", answers: b)

        #expect(SimpaticoV2State.score(between: stateA, and: stateB) == nil)
    }

    // MARK: - Perfect match

    @Test func perfectMatchReturns100() {
        var a: [String: SimpaticoV2Answer] = [:]
        var b: [String: SimpaticoV2Answer] = [:]
        for questionID in fiveQuestionIDs {
            let id = optionID(questionID, 0)
            a[questionID] = SimpaticoV2Answer(answer: id, acceptable: [id], importance: .very)
            b[questionID] = SimpaticoV2Answer(answer: id, acceptable: [id], importance: .very)
        }
        let stateA = SimpaticoV2State(userID: "a", answers: a)
        let stateB = SimpaticoV2State(userID: "b", answers: b)

        #expect(SimpaticoV2State.score(between: stateA, and: stateB) == 100)
    }

    // MARK: - A happy, B not

    @Test func aHappyButBNotYieldsLowScore() {
        var a: [String: SimpaticoV2Answer] = [:]
        var b: [String: SimpaticoV2Answer] = [:]
        for questionID in fiveQuestionIDs {
            let optionA = optionID(questionID, 0)
            let optionB = optionID(questionID, 1)
            // A answers optionA but accepts either — always satisfied by B's answer.
            a[questionID] = SimpaticoV2Answer(answer: optionA, acceptable: [optionA, optionB], importance: .very)
            // B answers optionB and only accepts optionB — never satisfied by A's answer.
            b[questionID] = SimpaticoV2Answer(answer: optionB, acceptable: [optionB], importance: .very)
        }
        let stateA = SimpaticoV2State(userID: "a", answers: a)
        let stateB = SimpaticoV2State(userID: "b", answers: b)

        let score = SimpaticoV2State.score(between: stateA, and: stateB)
        #expect(score != nil)
        #expect(score! < 50)
    }

    // MARK: - All "doesn't matter"

    @Test func allDoesNotMatterReturns100() {
        var a: [String: SimpaticoV2Answer] = [:]
        var b: [String: SimpaticoV2Answer] = [:]
        for questionID in fiveQuestionIDs {
            let optionA = optionID(questionID, 0)
            let optionB = optionID(questionID, 1)
            let allOptions = allOptionIDs(questionID)
            // Every option accepted => weight 0, regardless of what was picked as "your answer".
            a[questionID] = SimpaticoV2Answer(answer: optionA, acceptable: allOptions, importance: nil)
            b[questionID] = SimpaticoV2Answer(answer: optionB, acceptable: allOptions, importance: nil)
        }
        let stateA = SimpaticoV2State(userID: "a", answers: a)
        let stateB = SimpaticoV2State(userID: "b", answers: b)

        #expect(SimpaticoV2State.score(between: stateA, and: stateB) == 100)
    }
}
