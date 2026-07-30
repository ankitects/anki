// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct ScorePanelView: View {
    @ObservedObject var model: ScorePanelViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Evidence").font(.title3.bold())
                Spacer()
                Text("Bridge \(BuildInfo.displaySourceRevision)")
                    .font(.caption2.monospaced())
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("anki-bridge-source-revision")
                if model.isStale {
                    Label("Stale", systemImage: "clock.badge.exclamationmark")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
            }
            if let errorMessage = model.errorMessage {
                Label(errorMessage, systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if model.rows.isEmpty {
                ProgressView("Reading collection evidence…")
            } else {
                ForEach(model.rows) { row in
                    ScoreRowView(row: row)
                    if row.id != model.rows.last?.id {
                        Divider()
                    }
                }
            }
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}
