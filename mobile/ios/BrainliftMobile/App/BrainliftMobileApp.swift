// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI

@main
struct BrainliftMobileApp: App {
    private let backend = AppBootstrap.makeBackend()

    var body: some Scene {
        WindowGroup {
            ContentView(backend: backend)
        }
    }
}
