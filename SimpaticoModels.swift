//
//  SimpaticoModels.swift
//  Avenue3
//

import Foundation

// MARK: - Rating

enum SimpaticoRating: String, Codable, CaseIterable, Equatable {
    case notImportant   = "not"
    case somewhat       = "somewhat"
    case veryImportant  = "very"

    var displayName: String {
        switch self {
        case .notImportant:  return "Not important"
        case .somewhat:      return "Somewhat important"
        case .veryImportant: return "Very important"
        }
    }

    /// Short label used inside the three-option picker buttons.
    var shortName: String {
        switch self {
        case .notImportant:  return "Not"
        case .somewhat:      return "Somewhat"
        case .veryImportant: return "Very"
        }
    }
}

// MARK: - Category

enum SimpaticoCategory: String, CaseIterable {
    case sharedInterests    = "sharedInterests"
    case sharedValues       = "sharedValues"
    case clearCommunication = "clearCommunication"
    case mutualRespect      = "mutualRespect"
    case emotionalSupport   = "emotionalSupport"
    case conflictResolution = "conflictResolution"

    var displayName: String {
        switch self {
        case .sharedInterests:    return "Shared Interests & Hobbies"
        case .sharedValues:       return "Shared Values & Trust"
        case .clearCommunication: return "Clear Communication"
        case .mutualRespect:      return "Mutual Respect"
        case .emotionalSupport:   return "Emotional Support"
        case .conflictResolution: return "Conflict Resolution"
        }
    }
}

// MARK: - Component

/// The 18 questionnaire components, ordered as they appear in the flow.
enum SimpaticoComponent: String, CaseIterable {
    // Shared Interests & Hobbies
    case jointActivities = "jointActivities"
    case sharedLearning  = "sharedLearning"
    case qualityTime     = "qualityTime"

    // Shared Values & Trust
    case lifeAlignment = "lifeAlignment"
    case reliability   = "reliability"
    case vulnerability = "vulnerability"

    // Clear Communication
    case activeListening = "activeListening"
    case honesty         = "honesty"
    case openness        = "openness"

    // Mutual Respect
    case boundaries   = "boundaries"
    case appreciation = "appreciation"
    case equality     = "equality"

    // Emotional Support
    case empathy      = "empathy"
    case safety       = "safety"
    case encouragement = "encouragement"

    // Conflict Resolution
    case fairFighting = "fairFighting"
    case forgiveness  = "forgiveness"
    case compromise   = "compromise"

    var category: SimpaticoCategory {
        switch self {
        case .jointActivities, .sharedLearning, .qualityTime:
            return .sharedInterests
        case .lifeAlignment, .reliability, .vulnerability:
            return .sharedValues
        case .activeListening, .honesty, .openness:
            return .clearCommunication
        case .boundaries, .appreciation, .equality:
            return .mutualRespect
        case .empathy, .safety, .encouragement:
            return .emotionalSupport
        case .fairFighting, .forgiveness, .compromise:
            return .conflictResolution
        }
    }

    var displayName: String {
        switch self {
        case .jointActivities:  return "Joint activities"
        case .sharedLearning:   return "Shared learning"
        case .qualityTime:      return "Quality time"
        case .lifeAlignment:    return "Life alignment"
        case .reliability:      return "Reliability"
        case .vulnerability:    return "Vulnerability"
        case .activeListening:  return "Active listening"
        case .honesty:          return "Honesty"
        case .openness:         return "Openness"
        case .boundaries:       return "Boundaries"
        case .appreciation:     return "Appreciation"
        case .equality:         return "Equality"
        case .empathy:          return "Empathy"
        case .safety:           return "Safety"
        case .encouragement:    return "Encouragement"
        case .fairFighting:     return "Fair fighting"
        case .forgiveness:      return "Forgiveness"
        case .compromise:       return "Compromise"
        }
    }

    var componentDescription: String {
        switch self {
        case .jointActivities:  return "Doing things you both enjoy to build fun memories."
        case .sharedLearning:   return "Trying new skills or exploring fresh places together."
        case .qualityTime:      return "Dedicating focused, uninterrupted blocks of clock time to connect."
        case .lifeAlignment:    return "Sharing similar views on big goals like money or family."
        case .reliability:      return "Keeping your promises so your friend can count on you."
        case .vulnerability:    return "Sharing your deepest fears knowing your friend will care for them."
        case .activeListening:  return "Hear your friend out without planning your reply."
        case .honesty:          return "Share your true feelings in a kind way."
        case .openness:         return "Talk about hard things instead of hiding them."
        case .boundaries:       return "Give each other space to be yourselves."
        case .appreciation:     return "Thank your friend for small things."
        case .equality:         return "Make big choices together as a team."
        case .empathy:          return "Try to feel what your friend feels."
        case .safety:           return "Be a safe place when life is hard."
        case .encouragement:    return "Cheer for your friend's personal goals."
        case .fairFighting:     return "Attack the problem, not the person."
        case .forgiveness:      return "Let go of past mistakes after you talk them through."
        case .compromise:       return "Meet in the middle when you disagree."
        }
    }
}

// MARK: - Answer

struct SimpaticoAnswer: Codable, Equatable {
    /// How important this component is to you personally.
    var toYou: SimpaticoRating
    /// The level of importance you want this component to have for a friend.
    var toFriend: SimpaticoRating
}

// MARK: - Questionnaire

struct SimpaticoQuestionnaire {
    let userID: String
    /// Answers keyed by SimpaticoComponent.rawValue. Populated incrementally as the user progresses.
    var answers: [String: SimpaticoAnswer]
    var updatedAt: Date

    init(userID: String, answers: [String: SimpaticoAnswer] = [:], updatedAt: Date = Date()) {
        self.userID = userID
        self.answers = answers
        self.updatedAt = updatedAt
    }

    // MARK: - Completion

    /// True only when all 18 questions have been answered.
    var isComplete: Bool {
        answers.count == SimpaticoComponent.allCases.count
    }

    var answeredCount: Int { answers.count }

    func answer(for component: SimpaticoComponent) -> SimpaticoAnswer? {
        answers[component.rawValue]
    }

    mutating func setAnswer(_ answer: SimpaticoAnswer, for component: SimpaticoComponent) {
        answers[component.rawValue] = answer
        updatedAt = Date()
    }

    // MARK: - Score

    /// Returns the Simpatico score (0–100) between two users, or nil if either questionnaire is incomplete.
    /// A question matches only when A's "to you" exactly equals B's "to friend" AND vice versa.
    static func score(between a: SimpaticoQuestionnaire, and b: SimpaticoQuestionnaire) -> Int? {
        guard a.isComplete && b.isComplete else { return nil }
        let matched = SimpaticoComponent.allCases.filter { component in
            guard let answerA = a.answer(for: component),
                  let answerB = b.answer(for: component) else { return false }
            return answerA.toYou == answerB.toFriend && answerB.toYou == answerA.toFriend
        }.count
        return Int(Double(matched) / Double(SimpaticoComponent.allCases.count) * 100)
    }
}
