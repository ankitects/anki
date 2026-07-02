// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function isApplePlatform(): boolean {
    // avoid deprecation warning
    const platform = window.navigator["platform" + ""];
    return (
        platform.startsWith("Mac")
        || platform.startsWith("iP")
    );
}

export function isDesktop(): boolean {
    return !(/iphone|ipad|ipod|android/i.test(window.navigator.userAgent));
}

export function chromiumVersion(): number | null {
    const userAgent = window.navigator.userAgent;

    // Check if it's a Chromium-based browser (Chrome, Edge, Opera, etc.)
    // but exclude Safari which also contains "Chrome" in its user agent
    if (userAgent.includes("Safari") && !userAgent.includes("Chrome")) {
        return null; // Safari
    }

    const chromeMatch = userAgent.match(/Chrome\/(\d+)/);
    if (chromeMatch) {
        return parseInt(chromeMatch[1], 10);
    }

    return null; // Not a Chromium-based browser
}
