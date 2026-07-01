// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// App entry point. Owns the ReviewModel (@State, since the app creates it),
// kicks off the C3 startup on first appearance, and hands the model to the
// review screen. The whole app is one review loop on the shared Rust engine.

import SwiftUI

@main
struct AnkiMCATApp: App {
    @State private var model = ReviewModel()

    var body: some Scene {
        WindowGroup {
            ReviewView(model: model)
                .task {
                    await model.start()
                }
        }
    }
}
