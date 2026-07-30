// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct SyncProgressView: View {
    let progress: SyncProgress

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(progress.title)
            if let completed = progress.completed, let total = progress.total, total > 0 {
                ProgressView(value: Double(completed), total: Double(total))
                Text("\(completed) of \(total)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .accessibilityIdentifier("sync-progress")
    }
}
