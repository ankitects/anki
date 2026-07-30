// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import SwiftUI
import WebKit

enum CardWebSecurityPolicy {
    static let contentSecurityPolicy = [
        "default-src 'none'",
        "base-uri 'none'",
        "connect-src 'none'",
        "form-action 'none'",
        "frame-ancestors 'none'",
        "frame-src 'none'",
        "object-src 'none'",
        "worker-src 'none'",
        "img-src data: blob: file:",
        "media-src data: blob: file:",
        "font-src data: blob: file:",
        "style-src 'unsafe-inline' data: blob: file:",
        "script-src 'unsafe-inline'",
    ].joined(separator: "; ")

    static func securedHTML(_ html: String) -> String {
        let policy = """
        <meta http-equiv="Content-Security-Policy" \
        content="\(contentSecurityPolicy)">
        """
        if let head = html.range(
            of: #"(?i)<head(?:\s[^>]*)?>"#,
            options: .regularExpression
        ) {
            var secured = html
            secured.insert(contentsOf: policy, at: head.upperBound)
            return secured
        }
        if let htmlElement = html.range(
            of: #"(?i)<html(?:\s[^>]*)?>"#,
            options: .regularExpression
        ) {
            var secured = html
            secured.insert(
                contentsOf: "<head>\(policy)</head>",
                at: htmlElement.upperBound
            )
            return secured
        }
        return """
        <!doctype html>
        <html><head>\(policy)</head><body>\(html)</body></html>
        """
    }

    static func allowsTopLevelNavigation(
        to url: URL?,
        navigationType: WKNavigationType
    ) -> Bool {
        switch navigationType {
        case .linkActivated, .formSubmitted, .formResubmitted:
            return false
        default:
            break
        }
        guard let scheme = url?.scheme?.lowercased() else {
            return true
        }
        return ["about", "data", "blob", "file"].contains(scheme)
    }

    @MainActor
    static func makeConfiguration() -> WKWebViewConfiguration {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        return configuration
    }
}

struct CardWebView: UIViewRepresentable {
    let html: String

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView(
            frame: .zero,
            configuration: CardWebSecurityPolicy.makeConfiguration()
        )
        view.navigationDelegate = context.coordinator
        view.allowsLinkPreview = false
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard context.coordinator.loadedHTML != html else { return }
        context.coordinator.loadedHTML = html
        view.loadHTMLString(
            CardWebSecurityPolicy.securedHTML(html),
            baseURL: nil
        )
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var loadedHTML: String?

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction
        ) async -> WKNavigationActionPolicy {
            guard navigationAction.targetFrame?.isMainFrame ?? true else {
                return .allow
            }
            return CardWebSecurityPolicy.allowsTopLevelNavigation(
                to: navigationAction.request.url,
                navigationType: navigationAction.navigationType
            ) ? .allow : .cancel
        }
    }
}
