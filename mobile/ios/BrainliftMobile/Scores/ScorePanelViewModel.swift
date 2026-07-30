// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Foundation

@MainActor
final class ScorePanelViewModel: ObservableObject {
    @Published private(set) var rows: [EvidenceRow] = []
    @Published private(set) var isStale = true
    @Published private(set) var errorMessage: String?

    private let backend: any EvidenceBackend
    private let now: @Sendable () -> Date
    private let staleAfter: TimeInterval

    init(
        backend: any EvidenceBackend,
        now: @escaping @Sendable () -> Date = Date.init,
        staleAfter: TimeInterval = 86_400
    ) {
        self.backend = backend
        self.now = now
        self.staleAfter = staleAfter
    }

    func refresh() async {
        do {
            let snapshot = try await backend.evidenceSnapshot()
            rows = [
                EvidenceRow(title: "Memory", protobuf: snapshot.memory),
                EvidenceRow(title: "Performance", protobuf: snapshot.performance),
                EvidenceRow(title: "Readiness", protobuf: snapshot.readiness),
            ]
            let updatedAt = snapshot.updatedAtSecs > 0
                ? Date(timeIntervalSince1970: TimeInterval(snapshot.updatedAtSecs))
                : nil
            isStale = updatedAt.map { now().timeIntervalSince($0) > staleAfter } ?? true
            errorMessage = nil
        } catch {
            isStale = true
            errorMessage = error.localizedDescription
        }
    }
}
