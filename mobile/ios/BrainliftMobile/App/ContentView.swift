// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "Brainlift",
                systemImage: "brain.head.profile",
                description: Text("The shared Anki backend is ready.")
            )
            .navigationTitle("Brainlift")
        }
    }
}

#Preview {
    ContentView()
}
