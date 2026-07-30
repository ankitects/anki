// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct DeckPickerView: View {
    let decks: [ReviewDeck]
    let onSelect: (ReviewDeck) -> Void

    var body: some View {
        List(decks) { deck in
            Button {
                onSelect(deck)
            } label: {
                HStack {
                    Text(deck.name)
                    Spacer()
                    Text(deck.dueCount, format: .number)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("\(deck.dueCount) cards due")
                }
            }
            .accessibilityIdentifier("deck-\(deck.id)")
        }
        .overlay {
            if decks.isEmpty {
                ContentUnavailableView(
                    "No decks",
                    systemImage: "rectangle.stack",
                    description: Text("Add or sync a deck to begin reviewing.")
                )
            }
        }
    }
}

