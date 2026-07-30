// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation
import XCTest
@testable import BrainliftMobile

@MainActor
final class ReviewSessionViewModelTests: XCTestCase {
    func testNativeBackendReviewsFixtureUndoesAndPersistsAcrossReopen() async throws {
        let source = URL(
            filePath: #filePath
        )
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .appending(path: "rslib/tests/support/mediacheck.anki2")
        let directory = FileManager.default.temporaryDirectory
            .appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        try FileManager.default.copyItem(
            at: source,
            to: directory.appending(path: "collection.anki2")
        )
        defer { try? FileManager.default.removeItem(at: directory) }

        let backend = AnkiBackend(reviewCollectionDirectory: directory)
        try await backend.openReviewCollection()
        try await backend.checkReviewCollection()
        let decks = try await backend.reviewDecks()
        let deck = try XCTUnwrap(decks.first)
        try await backend.selectReviewDeck(id: deck.id)
        let firstCard = try await backend.nextReviewCard()
        let first = try XCTUnwrap(firstCard)

        try await answer(first, rating: .good, with: backend)
        let nextCard = try await backend.nextReviewCard()
        let afterAnswer = try XCTUnwrap(nextCard)
        XCTAssertNotEqual(afterAnswer.id, first.id)

        let canUndo = try await backend.canUndoReview()
        XCTAssertTrue(canUndo)
        try await backend.undoReview()
        let afterUndo = try await backend.nextReviewCard()
        XCTAssertEqual(afterUndo?.id, first.id)

        try await answer(first, rating: .good, with: backend)
        try await backend.closeReviewCollection()
        try await backend.openReviewCollection()
        try await backend.checkReviewCollection()
        try await backend.selectReviewDeck(id: deck.id)

        let afterReopen = try await backend.nextReviewCard()
        XCTAssertNotEqual(afterReopen?.id, first.id)
        try await backend.closeReviewCollection()
    }

    func testLoadsDeckAndRevealsRustRenderedAnswer() async {
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 2)],
            cards: [.fixture(id: 101, question: "<b>Question</b>", answer: "<i>Answer</i>")]
        )
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])

        XCTAssertEqual(model.phase, .question)
        XCTAssertEqual(model.displayedHTML, "<b>Question</b>")
        XCTAssertFalse(model.canGrade)

        model.revealAnswer()

        XCTAssertEqual(model.phase, .answer)
        XCTAssertEqual(model.displayedHTML, "<i>Answer</i>")
        XCTAssertTrue(model.canGrade)
    }

    func testGradeForwardsSelectedRustStateAndCustomDataThenLoadsNextCard() async {
        let first = ReviewCard.fixture(
            id: 101,
            question: "Q1",
            answer: "A1",
            customData: #"{"seed":"keep-me"}"#
        )
        let second = ReviewCard.fixture(id: 102, question: "Q2", answer: "A2")
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 2)],
            cards: [first, second]
        )
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])
        model.revealAnswer()
        await model.grade(.good)

        XCTAssertEqual(backend.answers.count, 1)
        XCTAssertEqual(backend.answers[0].rating, .good)
        XCTAssertEqual(backend.answers[0].cardID, first.id)
        XCTAssertEqual(
            try backend.answers[0].currentState.serializedData(),
            try first.currentState.serializedData()
        )
        var expectedNewState = first.states.good
        expectedNewState.customData = first.customData
        XCTAssertEqual(
            try backend.answers[0].newState.serializedData(),
            try expectedNewState.serializedData()
        )
        XCTAssertEqual(backend.answers[0].newState.customData, first.customData)
        XCTAssertEqual(model.card?.id, second.id)
        XCTAssertEqual(model.phase, .question)
        XCTAssertTrue(model.canUndo)
    }

    func testNoQueuedCardsProducesTerminalState() async {
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 0)],
            cards: []
        )
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])

        XCTAssertEqual(model.phase, .finished)
        XCTAssertNil(model.card)
        XCTAssertFalse(model.canGrade)
    }

    func testRenderingErrorDisablesGradingAndAllowsRetry() async {
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 1)],
            cards: []
        )
        backend.nextCardError = TestFailure.rendering
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])

        XCTAssertEqual(model.phase, .error)
        XCTAssertFalse(model.canGrade)
        XCTAssertTrue(model.canRetry)
        XCTAssertFalse(backend.didCloseCollection)

        backend.nextCardError = nil
        backend.cards = [.fixture(id: 101, question: "Q", answer: "A")]
        await model.retry()

        XCTAssertEqual(model.phase, .question)
        XCTAssertEqual(model.card?.id, 101)
    }

    func testUndoRestoresPreviousCardAndQuestionState() async {
        let first = ReviewCard.fixture(id: 101, question: "Q1", answer: "A1")
        let second = ReviewCard.fixture(id: 102, question: "Q2", answer: "A2")
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 2)],
            cards: [first, second]
        )
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])
        model.revealAnswer()
        await model.grade(.good)
        await model.undo()

        XCTAssertEqual(backend.undoCount, 1)
        XCTAssertEqual(model.card?.id, first.id)
        XCTAssertEqual(model.phase, .question)
        XCTAssertFalse(model.canUndo)
    }

    func testCloseAndReopenPreservesReviewAndChecksIntegrity() async {
        let first = ReviewCard.fixture(id: 101, question: "Q1", answer: "A1")
        let backend = ReviewBackendSpy(
            decks: [ReviewDeck(id: 7, name: "MCAT", dueCount: 1)],
            cards: [first]
        )
        let model = ReviewSessionViewModel(backend: backend)

        await model.start()
        await model.selectDeck(backend.decks[0])
        model.revealAnswer()
        await model.grade(.good)
        await model.close()

        backend.cards = []
        let reopened = ReviewSessionViewModel(backend: backend)
        await reopened.start()
        await reopened.selectDeck(backend.decks[0])

        XCTAssertTrue(backend.didCloseCollection)
        XCTAssertEqual(backend.openCount, 2)
        XCTAssertEqual(backend.integrityCheckCount, 2)
        XCTAssertEqual(backend.answers.count, 1)
        XCTAssertEqual(reopened.phase, .finished)
    }

    private func answer(
        _ card: ReviewCard,
        rating: ReviewRating,
        with backend: AnkiBackend
    ) async throws {
        var state = card.states.state(for: rating)
        state.customData = card.customData
        try await backend.answerReviewCard(
            ReviewAnswer(
                cardID: card.id,
                currentState: card.currentState,
                newState: state,
                rating: rating,
                answeredAtMillis: Int64(Date().timeIntervalSince1970 * 1_000),
                millisecondsTaken: 1_000
            )
        )
    }
}

private enum TestFailure: Error {
    case rendering
}

private final class ReviewBackendSpy: ReviewBackend, @unchecked Sendable {
    var decks: [ReviewDeck]
    var cards: [ReviewCard]
    var nextCardError: Error?
    private(set) var answers: [ReviewAnswer] = []
    private(set) var openCount = 0
    private(set) var integrityCheckCount = 0
    private(set) var undoCount = 0
    private(set) var didCloseCollection = false
    private var selectedDeckID: Int64?
    private var lastAnsweredCard: ReviewCard?

    init(decks: [ReviewDeck], cards: [ReviewCard]) {
        self.decks = decks
        self.cards = cards
    }

    func openReviewCollection() async throws {
        openCount += 1
    }

    func closeReviewCollection() async throws {
        didCloseCollection = true
    }

    func checkReviewCollection() async throws {
        integrityCheckCount += 1
    }

    func reviewDecks() async throws -> [ReviewDeck] {
        decks
    }

    func selectReviewDeck(id: Int64) async throws {
        selectedDeckID = id
    }

    func nextReviewCard() async throws -> ReviewCard? {
        if let nextCardError {
            throw nextCardError
        }
        guard selectedDeckID != nil else {
            return nil
        }
        return cards.first
    }

    func answerReviewCard(_ answer: ReviewAnswer) async throws {
        answers.append(answer)
        lastAnsweredCard = cards.removeFirst()
    }

    func canUndoReview() async throws -> Bool {
        lastAnsweredCard != nil
    }

    func undoReview() async throws {
        undoCount += 1
        if let lastAnsweredCard {
            cards.insert(lastAnsweredCard, at: 0)
            self.lastAnsweredCard = nil
        }
    }
}

private extension ReviewCard {
    static func fixture(
        id: Int64,
        question: String,
        answer: String,
        customData: String = ""
    ) -> ReviewCard {
        var current = Anki_Scheduler_SchedulingState()
        current.normal = .init()
        current.customData = customData

        func state(position: UInt32) -> Anki_Scheduler_SchedulingState {
            var state = Anki_Scheduler_SchedulingState()
            var normal = Anki_Scheduler_SchedulingState.Normal()
            var new = Anki_Scheduler_SchedulingState.New()
            new.position = position
            normal.new = new
            state.normal = normal
            return state
        }

        return ReviewCard(
            id: id,
            questionHTML: question,
            answerHTML: answer,
            currentState: current,
            states: ReviewSchedulingStates(
                again: state(position: 1),
                hard: state(position: 2),
                good: state(position: 3),
                easy: state(position: 4)
            ),
            customData: customData
        )
    }
}
