// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** Add night-mode class to documentElement if hash location is #night, and
    return true if added. */
export function checkNightMode(): boolean {
    // https://stackoverflow.com/a/57795518
    // This will be true in browsers if darkmode but also false in the reviewer if darkmode
    // If in the reviewer then this will need to be set by the python instead
    const nightMode = (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches)
        || window.location.hash == "#night";

    if (nightMode) {
        document.documentElement.className = "night-mode";
        document.documentElement.dataset.bsTheme = "dark";
    }
    return nightMode;
}
