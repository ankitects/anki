// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest
@testable import BrainliftMobile

@MainActor
final class ScorePanelTests: XCTestCase {
    func testAvailableAndAbstainedScoresPreserveBackendEvidence() async {
        let backend = EvidenceBackendStub(snapshot: .fixture())
        let model = ScorePanelViewModel(
            backend: backend,
            now: { Date(timeIntervalSince1970: 1_700_000_100) }
        )

        await model.refresh()

        XCTAssertEqual(model.rows.map(\.title), ["Memory", "Performance", "Readiness"])
        XCTAssertEqual(model.rows[0].state, .available)
        XCTAssertEqual(model.rows[0].estimate, 0.82)
        XCTAssertEqual(model.rows[0].range, EvidenceRange(lower: 0.74, upper: 0.88))
        XCTAssertEqual(model.rows[0].coverage, 0.75)
        XCTAssertEqual(model.rows[0].confidence, "Medium")
        XCTAssertEqual(model.rows[0].reasons, ["memory_from_ordinary_rated_reviews"])
        XCTAssertEqual(model.rows[2].state, .abstained)
        XCTAssertEqual(
            model.rows[2].reasons,
            ["readiness_score_mapping_not_validated"]
        )
        XCTAssertFalse(model.isStale)
        XCTAssertNil(model.errorMessage)
    }

    func testOldSnapshotIsMarkedStaleWithoutChangingItsValues() async {
        let backend = EvidenceBackendStub(snapshot: .fixture(updatedAt: 1_600_000_000))
        let model = ScorePanelViewModel(
            backend: backend,
            now: { Date(timeIntervalSince1970: 1_700_000_000) },
            staleAfter: 86_400
        )

        await model.refresh()

        XCTAssertTrue(model.isStale)
        XCTAssertEqual(model.rows[0].estimate, 0.82)
    }

    func testBackendErrorDoesNotPresentStaleValuesAsCurrent() async {
        let backend = EvidenceBackendStub(error: EvidenceFailure.unavailable)
        let model = ScorePanelViewModel(backend: backend)

        await model.refresh()

        XCTAssertEqual(model.rows, [])
        XCTAssertTrue(model.isStale)
        XCTAssertNotNil(model.errorMessage)
    }

    func testRefreshUsesDeterministicBackendWhenNetworkAndAIAreAbsent() async {
        let backend = EvidenceBackendStub(snapshot: .fixture())
        let model = ScorePanelViewModel(backend: backend)

        await model.refresh()
        await model.refresh()

        XCTAssertEqual(backend.requests, 2)
        XCTAssertEqual(model.rows[1].estimate, 0.66)
    }
}

private enum EvidenceFailure: Error {
    case unavailable
}

private final class EvidenceBackendStub: EvidenceBackend, @unchecked Sendable {
    private let snapshot: Anki_Stats_BrainliftScoreSnapshotResponse?
    private let error: Error?
    private(set) var requests = 0

    init(
        snapshot: Anki_Stats_BrainliftScoreSnapshotResponse? = nil,
        error: Error? = nil
    ) {
        self.snapshot = snapshot
        self.error = error
    }

    func evidenceSnapshot() async throws -> Anki_Stats_BrainliftScoreSnapshotResponse {
        requests += 1
        if let error {
            throw error
        }
        return try XCTUnwrap(snapshot)
    }
}

private extension Anki_Stats_BrainliftScoreSnapshotResponse {
    static func fixture(
        updatedAt: Int64 = 1_700_000_000
    ) -> Anki_Stats_BrainliftScoreSnapshotResponse {
        var snapshot = Self()
        snapshot.updatedAtSecs = updatedAt
        snapshot.memory = score(
            estimate: 0.82,
            range: (0.74, 0.88),
            coverage: 0.75,
            confidence: .medium,
            reason: "memory_from_ordinary_rated_reviews",
            updatedAt: updatedAt
        )
        snapshot.performance = score(
            estimate: 0.66,
            range: (0.55, 0.75),
            coverage: 0.5,
            confidence: .low,
            reason: "performance_from_held_out_rated_reviews",
            updatedAt: updatedAt
        )
        var readiness = Anki_Stats_BrainliftEvidenceScore()
        readiness.availability = .abstained
        readiness.scale = .mcat
        readiness.coverage = 0.5
        readiness.confidence = .none
        readiness.updatedAtSecs = updatedAt
        readiness.reasons = ["readiness_score_mapping_not_validated"]
        snapshot.readiness = readiness
        return snapshot
    }

    private static func score(
        estimate: Double,
        range: (Double, Double),
        coverage: Double,
        confidence: Anki_Stats_BrainliftEvidenceScore.Confidence,
        reason: String,
        updatedAt: Int64
    ) -> Anki_Stats_BrainliftEvidenceScore {
        var score = Anki_Stats_BrainliftEvidenceScore()
        score.availability = .available
        score.scale = .probability
        score.estimate = estimate
        score.range.lower = range.0
        score.range.upper = range.1
        score.coverage = coverage
        score.confidence = confidence
        score.updatedAtSecs = updatedAt
        score.reasons = [reason]
        return score
    }
}
