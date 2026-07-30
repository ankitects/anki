// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

enum AppBootstrap {
    static func makeBackend(
        arguments: [String] = ProcessInfo.processInfo.arguments
    ) -> any CompanionBackend {
        guard arguments.contains("--ui-testing") else {
            return AnkiBackend()
        }
        UserDefaults.standard.set(true, forKey: "hasCompletedInitialSync")
        return UITestCompanionBackend()
    }
}

private actor UITestCompanionBackend: CompanionBackend {
    private var hasCard = true
    private var canUndo = false

    func openReviewCollection() async throws {}
    func closeReviewCollection() async throws {}
    func checkReviewCollection() async throws {}

    func reviewDecks() async throws -> [ReviewDeck] {
        [ReviewDeck(id: 7, name: "MCAT Practice", dueCount: hasCard ? 1 : 0)]
    }

    func selectReviewDeck(id: Int64) async throws {}

    func nextReviewCard() async throws -> ReviewCard? {
        guard hasCard else { return nil }
        let state = Anki_Scheduler_SchedulingState()
        return ReviewCard(
            id: 101,
            questionHTML: "<html><body>What powers the cell?</body></html>",
            answerHTML: "<html><body>The mitochondrion.</body></html>",
            currentState: state,
            states: ReviewSchedulingStates(
                again: state,
                hard: state,
                good: state,
                easy: state
            ),
            customData: ""
        )
    }

    func answerReviewCard(_ answer: ReviewAnswer) async throws {
        hasCard = false
        canUndo = true
    }

    func canUndoReview() async throws -> Bool {
        canUndo
    }

    func undoReview() async throws {
        hasCard = true
        canUndo = false
    }

    func evidenceSnapshot() async throws
        -> Anki_Stats_BrainliftScoreSnapshotResponse
    {
        var abstained = Anki_Stats_BrainliftEvidenceScore()
        abstained.availability = .abstained
        abstained.scale = .probability
        abstained.confidence = .none
        abstained.reasons = ["insufficient_coverage"]
        var snapshot = Anki_Stats_BrainliftScoreSnapshotResponse()
        snapshot.memory = abstained
        snapshot.performance = abstained
        snapshot.readiness = abstained
        snapshot.updatedAtSecs = Int64(Date().timeIntervalSince1970)
        return snapshot
    }

    func syncLogin(
        credentials: SyncCredentials
    ) async throws -> Anki_Sync_SyncAuth {
        var auth = Anki_Sync_SyncAuth()
        auth.hkey = "ui-test-key"
        return auth
    }

    func syncCollection(
        auth: Anki_Sync_SyncAuth
    ) async throws -> SyncContinuation {
        SyncContinuation(required: .fullSync, auth: auth)
    }

    func fullSync(
        auth: Anki_Sync_SyncAuth,
        direction: SyncDirection
    ) async throws {}

    func latestSyncProgress() async throws -> SyncProgress? {
        nil
    }
}
