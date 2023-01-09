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
