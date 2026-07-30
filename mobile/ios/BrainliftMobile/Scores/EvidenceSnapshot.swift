// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

protocol EvidenceBackend: Sendable {
    func evidenceSnapshot() async throws -> Anki_Stats_BrainliftScoreSnapshotResponse
}

extension AnkiBackend: EvidenceBackend {
    func evidenceSnapshot() async throws -> Anki_Stats_BrainliftScoreSnapshotResponse {
        try brainliftScoreSnapshot(topics: EvidenceTopic.mcat.map { ($0.name, $0.tag) })
    }
}

struct EvidenceTopic: Sendable {
    let name: String
    let tag: String

    static let mcat = [
        EvidenceTopic(
            name: "Chemical and Physical Foundations",
            tag: "mcat::chemical-physical"
        ),
        EvidenceTopic(name: "Critical Analysis and Reasoning", tag: "mcat::cars"),
        EvidenceTopic(
            name: "Biological and Biochemical Foundations",
            tag: "mcat::biological-biochemical"
        ),
        EvidenceTopic(
            name: "Psychological, Social, and Biological Foundations",
            tag: "mcat::psychological-social"
        ),
    ]
}

struct EvidenceRange: Equatable, Sendable {
    let lower: Double
    let upper: Double
}

enum EvidenceState: Equatable, Sendable {
    case available
    case abstained
}

enum EvidenceScale: Equatable, Sendable {
    case probability
    case mcat
}

struct EvidenceRow: Equatable, Identifiable, Sendable {
    let title: String
    let state: EvidenceState
    let scale: EvidenceScale
    let estimate: Double
    let range: EvidenceRange?
    let coverage: Double
    let confidence: String
    let updatedAt: Date?
    let reasons: [String]

    var id: String { title }

    init(title: String, protobuf: Anki_Stats_BrainliftEvidenceScore) {
        self.title = title
        state = protobuf.availability == .available ? .available : .abstained
        scale = protobuf.scale == .mcat ? .mcat : .probability
        estimate = protobuf.estimate
        range = protobuf.hasRange
            ? EvidenceRange(lower: protobuf.range.lower, upper: protobuf.range.upper)
            : nil
        coverage = protobuf.coverage
        confidence = Self.confidenceLabel(protobuf.confidence)
        updatedAt = protobuf.updatedAtSecs > 0
            ? Date(timeIntervalSince1970: TimeInterval(protobuf.updatedAtSecs))
            : nil
        reasons = protobuf.reasons
    }

    private static func confidenceLabel(
        _ confidence: Anki_Stats_BrainliftEvidenceScore.Confidence
    ) -> String {
        switch confidence {
        case .none: "None"
        case .low: "Low"
        case .medium: "Medium"
        case .high: "High"
        case .UNRECOGNIZED: "Unknown"
        }
    }
}
