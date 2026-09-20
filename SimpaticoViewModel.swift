//
//  SimpaticoViewModel.swift
//  Avenue3
//

import Foundation
import Observation

@Observable
final class SimpaticoViewModel {

    // MARK: - State

    var questionnaire = SimpaticoQuestionnaire(userID: "")
    var currentIndex  = 0
    var selectedToYou:    SimpaticoRating? = nil
    var selectedToFriend: SimpaticoRating? = nil
    var isLoading  = true
    var isSaving   = false
    var errorMessage: String? = nil

    // MARK: - Services

    private let service = SimpaticoService.shared
    private let auth    = FirebaseAuthService.shared

    // MARK: - Derived

    private let components = SimpaticoComponent.allCases

    var currentComponent: SimpaticoComponent {
        components[max(0, min(currentIndex, components.count - 1))]
    }

    var totalCount:      Int  { components.count }
    var canGoBack:       Bool { currentIndex > 0 }
    var isLastQuestion:  Bool { currentIndex == totalCount - 1 }
    var canAdvance:      Bool { selectedToYou != nil && selectedToFriend != nil }

    // MARK: - Load

    func load() async {
        guard let userID = auth.currentUserID else { isLoading = false; return }
        isLoading = true
        defer { isLoading = false }
        do {
            questionnaire = try await service.fetchQuestionnaire(userID: userID)
            // Resume at the first question the user hasn't answered yet.
            if !questionnaire.isComplete {
                currentIndex = components.firstIndex(where: {
                    questionnaire.answer(for: $0) == nil
                }) ?? 0
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
    }

    func saveAndAdvance() async {
        guard let toYou = selectedToYou, let toFriend = selectedToFriend else { return }
        guard let userID = auth.currentUserID else { return }
        errorMessage = nil

        let answer = SimpaticoAnswer(toYou: toYou, toFriend: toFriend)
        // Update local model immediately so isComplete reacts without waiting for Firestore.
        questionnaire.setAnswer(answer, for: currentComponent)

        isSaving = true
        defer { isSaving = false }
        do {
            try await service.saveAnswer(answer, for: currentComponent, userID: userID)
        } catch {
            errorMessage = "Couldn't save. Please try again."
            return
        }

        // questionnaire.isComplete is now true if this was the last unanswered question —
        // the view reacts automatically. Only advance the index when there's a next question.
        guard !isLastQuestion else { return }
        currentIndex += 1
        syncPickers()
    }

    // MARK: - Helpers

    private func syncPickers() {
        let existing      = questionnaire.answer(for: currentComponent)
        selectedToYou     = existing?.toYou
        selectedToFriend  = existing?.toFriend
    }
}
