// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** Add night-mode class to documentElement if hash location is #night or the theme is set to dark in the browser, and
    return true if added. */
export function checkNightMode(): boolean {
    const nightMode = window.location.hash == "#night"
        // https://stackoverflow.com/a/57795518
        || (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches);

    if (nightMode) {
        document.documentElement.className = "night-mode";
        document.documentElement.dataset.bsTheme = "dark";
    }
    return nightMode;
}
