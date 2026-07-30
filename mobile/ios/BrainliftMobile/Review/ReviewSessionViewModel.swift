// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

@MainActor
final class ReviewSessionViewModel: ObservableObject {
    @Published private(set) var phase: ReviewPhase = .loading
    @Published private(set) var decks: [ReviewDeck] = []
    @Published private(set) var selectedDeck: ReviewDeck?
    @Published private(set) var card: ReviewCard?
    @Published private(set) var canUndo = false
    @Published private(set) var errorMessage: String?

    private let backend: any ReviewBackend
    private var questionStartedAt = Date()

    init(backend: any ReviewBackend) {
        self.backend = backend
    }

    var displayedHTML: String {
        guard let card else { return "" }
        return phase == .answer ? card.answerHTML : card.questionHTML
    }

    var canGrade: Bool {
        phase == .answer && card != nil
    }

    var canRetry: Bool {
        phase == .error && selectedDeck != nil
    }

    func start() async {
        phase = .loading
        errorMessage = nil
        do {
            try await backend.openReviewCollection()
            try await backend.checkReviewCollection()
            decks = try await backend.reviewDecks()
            phase = .choosingDeck
        } catch {
            fail(error)
        }
    }

    func selectDeck(_ deck: ReviewDeck) async {
        selectedDeck = deck
        phase = .loading
        errorMessage = nil
        do {
            try await backend.selectReviewDeck(id: deck.id)
            try await loadNextCard()
        } catch {
            fail(error)
        }
    }

    func revealAnswer() {
        guard phase == .question, card != nil else { return }
        phase = .answer
    }

    func grade(_ rating: ReviewRating) async {
        guard phase == .answer, let card else { return }
        phase = .loading
        var newState = card.states.state(for: rating)
        newState.customData = card.customData
        let elapsed = max(0, Date().timeIntervalSince(questionStartedAt))
        let milliseconds = UInt32(
            min(elapsed * 1_000, Double(UInt32.max))
        )
        let answer = ReviewAnswer(
            cardID: card.id,
            currentState: card.currentState,
            newState: newState,
            rating: rating,
            answeredAtMillis: Int64(Date().timeIntervalSince1970 * 1_000),
            millisecondsTaken: milliseconds
        )
        do {
            try await backend.answerReviewCard(answer)
            canUndo = try await backend.canUndoReview()
            try await loadNextCard()
        } catch {
            fail(error)
        }
    }

    func undo() async {
        guard canUndo else { return }
        phase = .loading
        do {
            try await backend.undoReview()
            canUndo = try await backend.canUndoReview()
            try await loadNextCard()
        } catch {
            fail(error)
        }
    }

    func retry() async {
        guard canRetry else { return }
        phase = .loading
        errorMessage = nil
        do {
            try await loadNextCard()
        } catch {
            fail(error)
        }
    }

    func close() async {
        do {
            try await backend.closeReviewCollection()
        } catch {
            fail(error)
        }
    }

    private func loadNextCard() async throws {
        card = try await backend.nextReviewCard()
        questionStartedAt = Date()
        phase = card == nil ? .finished : .question
    }

    private func fail(_ error: Error) {
        card = nil
        errorMessage = error.localizedDescription
        phase = .error
    }
}

