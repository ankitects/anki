// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Add dark-mode class to body if hash location is #dark, and return
/// true if added.
export function checkDarkMode(): boolean {
    const darkMode = window.location.hash == "#dark";
    if (darkMode) {
        document.documentElement.className = "dark-mode";
    }
    return darkMode;
}
