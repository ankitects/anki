// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { readable, get } from "svelte/store";
import { registerPackage } from "../lib/register-package";

interface ThemeInfo {
    isDark: boolean;
}

function getCurrentThemeFromRoot(): ThemeInfo {
    return {
        isDark: document.documentElement.classList.contains("night-mode"),
    };
}

let setCurrentTheme: ((theme: ThemeInfo) => void) | null = null;
export const currentTheme = readable(getCurrentThemeFromRoot(), (set) => {
    setCurrentTheme = set;
});
// ensure setCurrentTheme is set immediately
get(currentTheme);

// Update theme when root element's class changes.
const observer = new MutationObserver((_mutationsList, _observer) => {
    setCurrentTheme!(getCurrentThemeFromRoot());
});
observer.observe(document.documentElement, { attributeFilter: ["class"] });

registerPackage("anki/theme", {
    currentTheme,
});
