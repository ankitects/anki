// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// Live C3/C4 proof on the iOS simulator: launches the app (which opens a
// collection + imports the bundled .apkg in the Documents sandbox — C3),
// waits for the review screen, taps "Show Answer" then "Good", and asserts
// the loop advanced (C4 — a graded answer round-tripped through the shared
// Rust scheduler). Drives the real UI end-to-end via XCUITest.

import XCTest

final class ReviewFlowUITests: XCTestCase {
    func testReviewLoopRoundTrip() throws {
        let app = XCUIApplication()
        app.launch()

        // C3: after startup, the review screen shows a Show Answer button once
        // the card has been fetched + rendered from the imported collection.
        let showAnswer = app.buttons["Show answer"]
        XCTAssertTrue(showAnswer.waitForExistence(timeout: 30),
                      "Review screen with a rendered card should appear after import")

        // Reveal the answer, then grade Good — this calls answer_card through
        // the engine and advances the scheduler.
        showAnswer.tap()

        let good = app.buttons["Grade Good"]
        XCTAssertTrue(good.waitForExistence(timeout: 5),
                      "Grading buttons should appear after revealing the answer")
        good.tap()

        // C4: grading round-trips through the Rust scheduler and advances the
        // loop. The single new card graded Good becomes an intraday LEARNING
        // card due shortly, so the scheduler legitimately re-presents it (the
        // grading buttons disappear and a fresh Show Answer returns) rather
        // than emptying the queue — either that or "All caught up" is a valid
        // post-answer state. Assert on whichever appears; both prove the
        // answer was accepted and the loop moved forward.
        let advanced = expectation(description: "review loop advanced after grading")
        let showAgain = app.buttons["Show answer"]
        let done = app.staticTexts["All caught up"]
        // Poll for either terminal condition.
        let start = Date()
        DispatchQueue.global().async {
            while Date().timeIntervalSince(start) < 15 {
                if done.exists || showAgain.exists { advanced.fulfill(); return }
                Thread.sleep(forTimeInterval: 0.5)
            }
        }
        wait(for: [advanced], timeout: 16)
        XCTAssertTrue(done.exists || showAgain.exists,
                      "After grading, the loop should present the next card or finish")
    }
}
