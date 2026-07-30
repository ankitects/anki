// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import XCTest
@testable import BrainliftMobile

final class BuildInfoTests: XCTestCase {
    func testDisplayedRevisionMatchesTheLinkedBridgeAndBundleMarker() {
        let nativeRevision = String(cString: anki_backend_source_revision())

        XCTAssertFalse(nativeRevision.isEmpty)
        XCTAssertEqual(BuildInfo.sourceRevision, nativeRevision)
        XCTAssertEqual(BuildInfo.bundleSourceRevision, nativeRevision)
        XCTAssertTrue(BuildInfo.identityIsConsistent)
        XCTAssertEqual(
            BuildInfo.shortSourceRevision,
            String(nativeRevision.prefix(9))
                + (nativeRevision.hasSuffix("-dirty") ? "-dirty" : "")
        )
    }
}
