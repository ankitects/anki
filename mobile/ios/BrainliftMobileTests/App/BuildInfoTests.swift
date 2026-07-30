// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest
@testable import BrainliftMobile

final class BuildInfoTests: XCTestCase {
    func testAnkiCoreCommitIsEmbeddedAndDisplayable() {
        XCTAssertEqual(
            BuildInfo.ankiCoreCommit,
            "af5417a858cf979e4f9cadef02310d197fa52429"
        )
        XCTAssertEqual(BuildInfo.shortAnkiCoreCommit, "af5417a85")
    }
}
