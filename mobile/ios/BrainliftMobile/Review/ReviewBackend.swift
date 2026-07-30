// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

protocol ReviewBackend: Sendable {
    func openReviewCollection() async throws
    func closeReviewCollection() async throws
    func checkReviewCollection() async throws
    func reviewDecks() async throws -> [ReviewDeck]
    func selectReviewDeck(id: Int64) async throws
    func nextReviewCard() async throws -> ReviewCard?
    func answerReviewCard(_ answer: ReviewAnswer) async throws
    func canUndoReview() async throws -> Bool
    func undoReview() async throws
}

protocol CompanionBackend: ReviewBackend, EvidenceBackend, SyncBackend {}

extension AnkiBackend: ReviewBackend {
    func openReviewCollection() async throws {
        do {
            try open()
        } catch AnkiBackendError.alreadyOpen {
            // Reopening the collection keeps the single backend handle alive.
        }
        try openCollection(
            in: reviewCollectionDirectory ?? Self.defaultCollectionDirectory()
        )
    }

    func closeReviewCollection() async throws {
        try closeCollection()
    }

    func checkReviewCollection() async throws {
        let request = Anki_Generic_Empty()
        let _: Anki_Collection_CheckDatabaseResponse = try call(
            BackendMethods.backendCollectionServiceCheckDatabase,
            input: request
        )
    }

    func reviewDecks() async throws -> [ReviewDeck] {
        var request = Anki_Decks_DeckTreeRequest()
        request.now = Int64(Date().timeIntervalSince1970)
        let tree: Anki_Decks_DeckTreeNode = try call(
            BackendMethods.backendDecksServiceDeckTree,
            input: request
        )
        return Self.flattenDecks(tree.children)
    }

    func selectReviewDeck(id: Int64) async throws {
        var request = Anki_Decks_DeckId()
        request.did = id
        let _: Anki_Collection_OpChanges = try call(
            BackendMethods.backendDecksServiceSetCurrentDeck,
            input: request
        )
    }

    func nextReviewCard() async throws -> ReviewCard? {
        var queueRequest = Anki_Scheduler_GetQueuedCardsRequest()
        queueRequest.fetchLimit = 1
        let queue: Anki_Scheduler_QueuedCards = try call(
            BackendMethods.backendSchedulerServiceGetQueuedCards,
            input: queueRequest
        )
        guard let queued = queue.cards.first else {
            return nil
        }

        var renderRequest = Anki_CardRendering_RenderExistingCardRequest()
        renderRequest.cardID = queued.card.id
        let rendered: Anki_CardRendering_RenderCardResponse = try call(
            BackendMethods.backendCardRenderingServiceRenderExistingCard,
            input: renderRequest
        )
        guard !rendered.isEmpty else {
            throw ReviewBackendError.emptyRenderedCard
        }

        return ReviewCard(
            id: queued.card.id,
            questionHTML: Self.html(
                nodes: rendered.questionNodes,
                css: rendered.css
            ),
            answerHTML: Self.html(
                nodes: rendered.answerNodes,
                css: rendered.css
            ),
            currentState: queued.states.current,
            states: ReviewSchedulingStates(
                again: queued.states.again,
                hard: queued.states.hard,
                good: queued.states.good,
                easy: queued.states.easy
            ),
            customData: queued.card.customData
        )
    }

    func answerReviewCard(_ answer: ReviewAnswer) async throws {
        var request = Anki_Scheduler_CardAnswer()
        request.cardID = answer.cardID
        request.currentState = answer.currentState
        request.newState = answer.newState
        request.rating = answer.rating.backendRating
        request.answeredAtMillis = answer.answeredAtMillis
        request.millisecondsTaken = answer.millisecondsTaken
        let _: Anki_Collection_OpChanges = try call(
            BackendMethods.backendSchedulerServiceAnswerCard,
            input: request
        )
    }

    func canUndoReview() async throws -> Bool {
        let request = Anki_Generic_Empty()
        let status: Anki_Collection_UndoStatus = try call(
            BackendMethods.backendCollectionServiceGetUndoStatus,
            input: request
        )
        return !status.undo.isEmpty
    }

    func undoReview() async throws {
        let request = Anki_Generic_Empty()
        let _: Anki_Collection_OpChangesAfterUndo = try call(
            BackendMethods.backendCollectionServiceUndo,
            input: request
        )
    }

    private static func defaultCollectionDirectory() -> URL {
        let base = FileManager.default.urls(
            for: .documentDirectory,
            in: .userDomainMask
        ).first ?? FileManager.default.temporaryDirectory
        return base.appending(
            path: "BrainliftMobile",
            directoryHint: .isDirectory
        )
    }

    private static func flattenDecks(
        _ nodes: [Anki_Decks_DeckTreeNode]
    ) -> [ReviewDeck] {
        var decks: [ReviewDeck] = []
        appendDecks(nodes, parent: nil, to: &decks)
        return decks
    }

    private static func appendDecks(
        _ nodes: [Anki_Decks_DeckTreeNode],
        parent: String?,
        to decks: inout [ReviewDeck]
    ) {
        for node in nodes {
            let name = parent.map { "\($0)::\(node.name)" } ?? node.name
            decks.append(
                ReviewDeck(
                    id: node.deckID,
                    name: name,
                    dueCount: node.newCount + node.learnCount + node.reviewCount
                )
            )
            appendDecks(node.children, parent: name, to: &decks)
        }
    }

    private static func html(
        nodes: [Anki_CardRendering_RenderedTemplateNode],
        css: String
    ) -> String {
        let body = nodes.map { node in
            switch node.value {
            case .text(let text):
                text
            case .replacement(let replacement):
                replacement.currentText
            case nil:
                ""
            }
        }.joined()
        return """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>\(css)</style>
          </head>
          <body class="card">\(body)</body>
        </html>
        """
    }
}

extension AnkiBackend: CompanionBackend {}

enum ReviewBackendError: LocalizedError {
    case emptyRenderedCard

    var errorDescription: String? {
        switch self {
        case .emptyRenderedCard:
            "Rust rendered an empty card."
        }
    }
}
