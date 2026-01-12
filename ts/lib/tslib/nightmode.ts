// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

type ThemePreference = "light" | "dark" | "system";

const THEME_STORAGE_KEY = "ankiThemePreference";
const DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)";

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((event: MediaQueryListEvent) => void) | null = null;

function themeFromHash(): ThemePreference | null {
    const hash = window.location.hash.toLowerCase();
    if (hash === "#night" || hash === "#dark") {
        return "dark";
    }
    if (hash === "#light" || hash === "#day") {
        return "light";
    }
    return null;
}

function readStoredPreference(): ThemePreference | null {
    try {
        const value = window.localStorage.getItem(THEME_STORAGE_KEY);
        if (value === "light" || value === "dark" || value === "system") {
            return value;
        }
    } catch {
        // Storage may be unavailable in some contexts.
    }
    return null;
}

function writeStoredPreference(preference: ThemePreference): void {
    try {
        window.localStorage.setItem(THEME_STORAGE_KEY, preference);
    } catch {
        // Ignore storage failures and keep applying in-memory.
    }
}

function prefersDarkMode(): boolean {
    if (!window.matchMedia) {
        return false;
    }
    return window.matchMedia(DARK_MEDIA_QUERY).matches;
}

function applyTheme(isDark: boolean): void {
    document.documentElement.classList.toggle("night-mode", isDark);
    document.documentElement.dataset.bsTheme = isDark ? "dark" : "light";
}

function resolveTheme(): { preference: ThemePreference; isDark: boolean } {
    const hashPreference = themeFromHash();
    const storedPreference = readStoredPreference() ?? "system";
    const preference = hashPreference ?? storedPreference;
    const isDark =
        preference === "dark" ? true : preference === "light" ? false : prefersDarkMode();
    return { preference, isDark };
}

function ensureMediaListener(): void {
    if (!window.matchMedia) {
        return;
    }
    if (!mediaQuery) {
        mediaQuery = window.matchMedia(DARK_MEDIA_QUERY);
    }
    if (!mediaListener) {
        mediaListener = (event: MediaQueryListEvent) => {
            const { preference } = resolveTheme();
            if (preference === "system" && themeFromHash() === null) {
                applyTheme(event.matches);
            }
        };
        if ("addEventListener" in mediaQuery) {
            mediaQuery.addEventListener("change", mediaListener);
        } else {
            mediaQuery.addListener(mediaListener);
        }
    }
}

/** Apply theme based on URL hash, stored preference, or system settings. */
export function checkNightMode(): boolean {
    const { isDark } = resolveTheme();
    applyTheme(isDark);
    ensureMediaListener();
    return isDark;
}

export function setThemePreference(preference: ThemePreference): void {
    writeStoredPreference(preference);
    checkNightMode();
}

export function getThemePreference(): ThemePreference {
    return readStoredPreference() ?? "system";
}
