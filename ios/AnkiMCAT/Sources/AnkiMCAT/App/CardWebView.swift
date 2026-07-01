// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// CardWebView — renders a card's field HTML + template CSS in a WKWebView.
// Anki cards are HTML/CSS, so a web view is the faithful renderer for the
// question/answer sides (the desktop/mobile reviewers do the same). For the
// MVP we load a self-contained HTML string; no JS bridge is needed.

import SwiftUI
import WebKit

struct CardWebView: UIViewRepresentable {
    let html: String
    let css: String

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        webView.loadHTMLString(document, baseURL: nil)
    }

    /// Wrap the card HTML in a minimal document with the template CSS and a
    /// responsive viewport, mirroring how the reviewer hosts a card.
    private var document: String {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
        :root { color-scheme: light dark; }
        body {
            font-family: -apple-system, system-ui, sans-serif;
            font-size: 20px;
            text-align: center;
            margin: 16px;
        }
        \(css)
        </style>
        </head>
        <body class="card">
        \(html)
        </body>
        </html>
        """
    }
}
