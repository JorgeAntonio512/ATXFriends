//
//  SimpaticoModels.swift
//  Avenue3
//

import Foundation

// MARK: - Category

enum SimpaticoCategory: String, CaseIterable {
    case hangingOut  = "hangingOut"
    case socialStyle = "socialStyle"
    case lifestyle   = "lifestyle"

    var displayName: String {
        switch self {
        case .hangingOut:  return "Hanging Out"
        case .socialStyle: return "Social Style"
        case .lifestyle:   return "Lifestyle"
        }
    }
}

// MARK: - Importance

enum SimpaticoImportance: String, Codable, CaseIterable, Equatable {
    case little   = "little"
    case somewhat = "somewhat"
    case very     = "very"

    var displayName: String {
        switch self {
        case .little:   return "A little"
        case .somewhat: return "Somewhat"
        case .very:     return "Very"
        }
    }

    var weight: Int {
        switch self {
        case .little:   return 1
        case .somewhat: return 10
        case .very:     return 50
        }
    }
}

// MARK: - Option

struct SimpaticoOption: Identifiable, Equatable, Hashable {
    let id: String
    let text: String
}

// MARK: - Question

struct SimpaticoQuestion: Identifiable, Equatable {
    let id: String
    let category: SimpaticoCategory
    let prompt: String
    let options: [SimpaticoOption]

    static func == (lhs: SimpaticoQuestion, rhs: SimpaticoQuestion) -> Bool { lhs.id == rhs.id }
}

// MARK: - Question bank

/// The 12 fixed questions, in the order they appear in the flow.
enum SimpaticoQuestionBank {
    static let all: [SimpaticoQuestion] = [
        // Hanging Out
        SimpaticoQuestion(
            id: "saturday",
            category: .hangingOut,
            prompt: "Free Saturday, I'd rather…",
            options: [
                SimpaticoOption(id: "outdoors", text: "Get outside and active"),
                SimpaticoOption(id: "explore", text: "Explore somewhere new"),
                SimpaticoOption(id: "chill", text: "Chill at someone's place"),
                SimpaticoOption(id: "goOut", text: "Go out: bars, shows, events")
            ]
        ),
        SimpaticoQuestion(
            id: "hangSize",
            category: .hangingOut,
            prompt: "My ideal hang is…",
            options: [
                SimpaticoOption(id: "oneOnOne", text: "One-on-one"),
                SimpaticoOption(id: "smallCrew", text: "A small crew (3–5)"),
                SimpaticoOption(id: "bigGroup", text: "The bigger the better")
            ]
        ),
        SimpaticoQuestion(
            id: "planning",
            category: .hangingOut,
            prompt: "When it comes to plans, I'm…",
            options: [
                SimpaticoOption(id: "planner", text: "A planner"),
                SimpaticoOption(id: "loose", text: "Loose plan, figure it out day-of"),
                SimpaticoOption(id: "spontaneous", text: "Spontaneous: text me in an hour")
            ]
        ),
        SimpaticoQuestion(
            id: "bestTime",
            category: .hangingOut,
            prompt: "I'm at my best…",
            options: [
                SimpaticoOption(id: "earlyMorning", text: "Early mornings"),
                SimpaticoOption(id: "daytime", text: "Daytime"),
                SimpaticoOption(id: "evening", text: "Evenings"),
                SimpaticoOption(id: "lateNight", text: "Late nights")
            ]
        ),

        // Social Style
        SimpaticoQuestion(
            id: "socialBattery",
            category: .socialStyle,
            prompt: "After a big social weekend, I feel…",
            options: [
                SimpaticoOption(id: "energized", text: "Energized: what's next?"),
                SimpaticoOption(id: "quietDay", text: "Good, but I need a quiet day"),
                SimpaticoOption(id: "wiped", text: "Wiped for a few days")
            ]
        ),
        SimpaticoQuestion(
            id: "convoDepth",
            category: .socialStyle,
            prompt: "With a new friend, I like conversations…",
            options: [
                SimpaticoOption(id: "light", text: "Light and fun"),
                SimpaticoOption(id: "mix", text: "A mix"),
                SimpaticoOption(id: "deep", text: "Deep pretty quick")
            ]
        ),
        SimpaticoQuestion(
            id: "texting",
            category: .socialStyle,
            prompt: "Between hangouts, I text…",
            options: [
                SimpaticoOption(id: "daily", text: "Daily-ish"),
                SimpaticoOption(id: "fewTimesWeek", text: "A few times a week"),
                SimpaticoOption(id: "plansOnly", text: "Only to make plans")
            ]
        ),
        SimpaticoQuestion(
            id: "humor",
            category: .socialStyle,
            prompt: "My humor is…",
            options: [
                SimpaticoOption(id: "dry", text: "Dry and sarcastic"),
                SimpaticoOption(id: "silly", text: "Silly and goofy"),
                SimpaticoOption(id: "dark", text: "Dark"),
                SimpaticoOption(id: "wholesome", text: "Wholesome")
            ]
        ),

        // Lifestyle
        SimpaticoQuestion(
            id: "drinking",
            category: .lifestyle,
            prompt: "Drinking: I…",
            options: [
                SimpaticoOption(id: "regularly", text: "Drink regularly"),
                SimpaticoOption(id: "socially", text: "Drink socially"),
                SimpaticoOption(id: "rarely", text: "Rarely drink"),
                SimpaticoOption(id: "never", text: "Don't drink")
            ]
        ),
        SimpaticoQuestion(
            id: "nightOutCost",
            category: .lifestyle,
            prompt: "A fun night out should cost…",
            options: [
                SimpaticoOption(id: "under20", text: "Under $20"),
                SimpaticoOption(id: "20to50", text: "$20–50"),
                SimpaticoOption(id: "over50", text: "$50+"),
                SimpaticoOption(id: "whatever", text: "Whatever, if it's worth it")
            ]
        ),
        SimpaticoQuestion(
            id: "punctuality",
            category: .lifestyle,
            prompt: "Being on time means…",
            options: [
                SimpaticoOption(id: "early", text: "Early is on time"),
                SimpaticoOption(id: "tenMin", text: "Within 10 minutes is fine"),
                SimpaticoOption(id: "suggestion", text: "Time is a suggestion")
            ]
        ),
        SimpaticoQuestion(
            id: "schedule",
            category: .lifestyle,
            prompt: "My schedule is…",
            options: [
                SimpaticoOption(id: "nineToFive", text: "Steady 9-to-5"),
                SimpaticoOption(id: "shifts", text: "Shifts, all over the place"),
                SimpaticoOption(id: "flexible", text: "Pretty flexible"),
                SimpaticoOption(id: "parent", text: "Parent life: it's chaos")
            ]
        )
    ]

    private static let byID: [String: SimpaticoQuestion] = Dictionary(
        uniqueKeysWithValues: all.map { ($0.id, $0) }
    )

    static func question(id: String) -> SimpaticoQuestion? { byID[id] }
}

// MARK: - Answer

struct SimpaticoV2Answer: Codable, Equatable {
    /// The option ID the user picked for "Your answer".
    var answer: String
    /// Option IDs the user is good with a friend answering ("I'm good with friends who say…").
    var acceptable: [String]
    /// How much this matters to the user. Absent when every option is acceptable ("Doesn't matter").
    var importance: SimpaticoImportance?

    /// The weight this answer contributes to the score. 0 when every option was marked acceptable.
    func weight(totalOptions: Int) -> Int {
        if acceptable.count >= totalOptions { return 0 }
        return importance?.weight ?? 0
    }
}

// MARK: - Questionnaire state

struct SimpaticoV2State {
    let userID: String
    /// v2 answers keyed by question ID. A skipped question is absent from this map.
    var answers: [String: SimpaticoV2Answer]
    var completedAt: Date?
    /// True when the legacy (pre-v2) "answers" field has data. Drives the intro's upgrade banner.
    var hasLegacyAnswers: Bool

    init(userID: String, answers: [String: SimpaticoV2Answer] = [:], completedAt: Date? = nil, hasLegacyAnswers: Bool = false) {
        self.userID = userID
        self.answers = answers
        self.completedAt = completedAt
        self.hasLegacyAnswers = hasLegacyAnswers
    }

    // MARK: - Score

    /// Returns the symmetric Simpatico score (0–100) between two users, or nil if they
    /// share fewer than 5 answered questions.
    static func score(between a: SimpaticoV2State, and b: SimpaticoV2State) -> Int? {
        let sharedIDs = Set(a.answers.keys).intersection(b.answers.keys)
        guard sharedIDs.count >= 5 else { return nil }

        let satisfactionA = satisfaction(of: a.answers, ratedBy: b.answers, sharedIDs: sharedIDs)
        let satisfactionB = satisfaction(of: b.answers, ratedBy: a.answers, sharedIDs: sharedIDs)

        return Int((satisfactionA * satisfactionB).squareRoot().rounded())
    }

    /// `subject`'s satisfaction: the fraction of `subject`'s weight, across shared questions,
    /// where `other`'s answer falls within `subject`'s acceptable list. 1.0 if `subject`
    /// placed zero total weight on the shared questions (everything was "doesn't matter").
    private static func satisfaction(
        of subject: [String: SimpaticoV2Answer],
        ratedBy other: [String: SimpaticoV2Answer],
        sharedIDs: Set<String>
    ) -> Double {
        var totalWeight = 0.0
        var satisfiedWeight = 0.0

        for questionID in sharedIDs {
            guard
                let subjectAnswer = subject[questionID],
                let otherAnswer = other[questionID],
                let totalOptions = SimpaticoQuestionBank.question(id: questionID)?.options.count
            else { continue }

            let weight = Double(subjectAnswer.weight(totalOptions: totalOptions))
            totalWeight += weight
            if subjectAnswer.acceptable.contains(otherAnswer.answer) {
                satisfiedWeight += weight
            }
        }

        guard totalWeight > 0 else { return 1.0 }
        return satisfiedWeight / totalWeight
    }
}
