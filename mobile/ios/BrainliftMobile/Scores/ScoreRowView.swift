// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct ScoreRowView: View {
    let row: EvidenceRow

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(row.title).font(.headline)
                Spacer()
                Text(primaryValue)
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(row.state == .available ? .primary : .secondary)
            }
            if let range = row.range, row.state == .available {
                Text("Likely range \(format(range.lower))–\(format(range.upper))")
                    .font(.subheadline)
            }
            HStack {
                Label(
                    "\(row.coverage, format: .percent.precision(.fractionLength(0))) covered",
                    systemImage: "square.grid.2x2"
                )
                Spacer()
                Text("\(row.confidence) confidence")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            if !row.reasons.isEmpty {
                Text(row.reasons.map(humanize).joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("score-\(row.title.lowercased())")
    }

    private var primaryValue: String {
        guard row.state == .available else { return "Not enough evidence" }
        return format(row.estimate)
    }

    private func format(_ value: Double) -> String {
        switch row.scale {
        case .probability:
            value.formatted(.percent.precision(.fractionLength(0)))
        case .mcat:
            value.formatted(.number.precision(.fractionLength(0)))
        }
    }

    private func humanize(_ reason: String) -> String {
        reason
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: ":", with: ": ")
            .capitalized
    }
}
