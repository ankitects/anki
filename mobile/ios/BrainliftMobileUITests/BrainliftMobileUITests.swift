// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest

@MainActor
final class BrainliftMobileUITests: XCTestCase {
    func testReviewRevealGradeAndUndoFlow() {
        let app = launchApp()
        let deck = app.buttons["deck-7"]
        XCTAssertTrue(deck.waitForExistence(timeout: 5))
        deck.tap()

        let showAnswer = app.buttons["show-answer"]
        XCTAssertTrue(showAnswer.waitForExistence(timeout: 5))
        showAnswer.tap()

        let good = app.buttons["grade-good"]
        XCTAssertTrue(good.waitForExistence(timeout: 5))
        good.tap()

        let undo = app.buttons["undo-review"]
        XCTAssertTrue(undo.waitForExistence(timeout: 5))
        undo.tap()
        XCTAssertTrue(showAnswer.waitForExistence(timeout: 5))
    }

    func testEvidencePanelShowsAbstention() {
        let app = launchApp()
        let abstention = app.staticTexts["Not enough evidence"].firstMatch
        XCTAssertTrue(abstention.waitForExistence(timeout: 5))
    }

    func testLaterFullSyncRequiresExplicitDirection() {
        let app = launchApp()
        let sync = app.buttons["open-sync"]
        XCTAssertTrue(sync.waitForExistence(timeout: 5))
        sync.tap()

        let username = app.textFields["sync-username"]
        XCTAssertTrue(username.waitForExistence(timeout: 5))
        username.tap()
        username.typeText("learner@example.com")

        let password = app.secureTextFields["sync-password"]
        password.tap()
        password.typeText("secret")
        app.buttons["save-and-sync"].tap()

        XCTAssertTrue(
            app.buttons["full-sync-download"].waitForExistence(timeout: 5)
        )
        XCTAssertTrue(app.buttons["full-sync-upload"].exists)
    }

    private func launchApp() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()
        return app
    }
}
