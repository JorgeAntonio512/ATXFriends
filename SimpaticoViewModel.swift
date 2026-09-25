//
//  SimpaticoViewModel.swift
//  Avenue3
//

import Foundation
import Observation

@Observable
final class SimpaticoViewModel {

    // MARK: - State

    var state = SimpaticoV2State(userID: "")
    var currentIndex = 0
    var selectedAnswerID: String? = nil
    var selectedAcceptable: Set<String> = []
    var selectedImportance: SimpaticoImportance? = nil
    var isLoading  = true
    var isSaving   = false
    var errorMessage: String? = nil
    /// True while re-entering the flow from the Complete screen's "Edit answers" button,
    /// so the flow shows even though `state.completedAt` is already set.
    var isEditing = false

    // MARK: - Services

    private let service = SimpaticoService.shared
    private let auth    = FirebaseAuthService.shared

    // MARK: - Derived

    let questions = SimpaticoQuestionBank.all

    var currentQuestion: SimpaticoQuestion {
        questions[max(0, min(currentIndex, questions.count - 1))]
    }

    var totalCount:      Int  { questions.count }
    var answeredCount:   Int  { state.answers.count }
    var canGoBack:       Bool { currentIndex > 0 }
    var isLastQuestion:  Bool { currentIndex == totalCount - 1 }
    var isComplete:      Bool { state.completedAt != nil && !isEditing }

    /// True once every option in "I'm good with friends who say…" is checked —
    /// the importance question is hidden and this question gets weight 0.
    var doesNotMatter: Bool { selectedAcceptable.count == currentQuestion.options.count }

    var canAdvance: Bool {
        guard selectedAnswerID != nil else { return false }
        return doesNotMatter || selectedImportance != nil
    }

    /// Shows the upgrade banner on the intro screen: the user answered the old
    /// questionnaire but hasn't started v2 yet.
    var showUpgradeBanner: Bool { state.hasLegacyAnswers && state.answers.isEmpty }

    // MARK: - Load

    func load() async {
        guard let userID = auth.currentUserID else { isLoading = false; return }
        isLoading = true
        defer { isLoading = false }
        do {
            state = try await service.fetchState(userID: userID)
            if state.completedAt == nil {
                currentIndex = min(max(0, savedPosition(userID: userID)), totalCount - 1)
            }
            syncPickers()
        } catch {
            errorMessage = "Couldn't load your answers. Please try again."
        }
    }

    // MARK: - Navigation

    func goBack() {
        guard canGoBack else { return }
        currentIndex -= 1
        syncPickers()
        errorMessage = nil
        if let userID = auth.currentUserID { savePosition(userID: userID, index: currentIndex) }
    }

    func selectAnswer(_ optionID: String) {
        selectedAnswerID = optionID
        selectedAcceptable.insert(optionID)
    }

    func toggleAcceptable(_ optionID: String) {
        if selectedAcceptable.contains(optionID) {
            selectedAcceptable.remove(optionID)
        } else {
            selectedAcceptable.insert(optionID)
        }
        if doesNotMatter { selectedImportance = nil }
    }

    func selectImportance(_ importance: SimpaticoImportance) {
        selectedImportance = importance
    }

    func skip() async {
        errorMessage = nil
        await advance()
    }

    func saveAndAdvance() async {
        guard canAdvance, let answerID = selectedAnswerID, let userID = auth.currentUserID else { return }
        errorMessage = nil

        let answer = SimpaticoV2Answer(
            answer: answerID,
            acceptable: Array(selectedAcceptable),
            importance: doesNotMatter ? nil : selectedImportance
        )
        // Update local state immediately so answeredCount reacts without waiting for Firestore.
        state.answers[currentQuestion.id] = answer

        isSaving = true
        defer { isSaving = false }
        do {
            try await service.saveV2Answer(answer, for: currentQuestion.id, userID: userID)
        } catch {
            errorMessage = "Couldn't save. Please try again."
            return
        }
        await advance()
    }

    /// Re-enters the flow from question 1 with every saved answer prefilled.
    func startEditing() {
        isEditing = true
        currentIndex = 0
        syncPickers()
    }

    // MARK: - Helpers

    private func advance() async {
        guard let userID = auth.currentUserID else { return }
        guard !isLastQuestion else {
            do {
                try await service.markV2Complete(userID: userID)
                state.completedAt = Date()
                isEditing = false
                clearPosition(userID: userID)
            } catch {
                errorMessage = "Couldn't finish. Please try again."
            }
            return
        }
        currentIndex += 1
        savePosition(userID: userID, index: currentIndex)
        syncPickers()
    }

    private func syncPickers() {
        let existing = state.answers[currentQuestion.id]
        selectedAnswerID    = existing?.answer
        selectedAcceptable  = existing.map { Set($0.acceptable) } ?? []
        selectedImportance  = existing?.importance
    }

    // MARK: - Local position (Firestore has no record of skipped questions)

    private func positionKey(_ userID: String) -> String { Self.positionKey(userID) }

    private static func positionKey(_ userID: String) -> String { "simpaticoV2Position.\(userID)" }

    /// Forgets a user's saved question position (used on account deletion).
    static func clearSavedPosition(userID: String) {
        UserDefaults.standard.removeObject(forKey: positionKey(userID))
    }

    private func savedPosition(userID: String) -> Int {
        UserDefaults.standard.integer(forKey: positionKey(userID))
    }

    private func savePosition(userID: String, index: Int) {
        UserDefaults.standard.set(index, forKey: positionKey(userID))
    }

    private func clearPosition(userID: String) {
        UserDefaults.standard.removeObject(forKey: positionKey(userID))
    }
}
