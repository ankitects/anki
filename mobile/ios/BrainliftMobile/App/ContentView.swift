// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

struct ContentView: View {
    let backend: any CompanionBackend

    init(backend: any CompanionBackend = AnkiBackend()) {
        self.backend = backend
    }

    var body: some View {
        NavigationStack {
            ReviewSessionView(backend: backend)
        }
    }
}

#Preview {
    ContentView()
}
