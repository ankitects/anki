// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// import { checkNightMode } from "@tslib/nightmode";
import type { LayoutLoad } from "./$types";

// Tauri doesn't have a Node.js server to do proper SSR
// so we use adapter-static with a fallback to index.html to put the site in SPA mode
// See: https://svelte.dev/docs/kit/single-page-apps
// See: https://v2.tauri.app/start/frontend/sveltekit/ for more info
export const ssr = false;

export const load: LayoutLoad = async () => {
    // checkNightMode();
    // TODO: don't force nightmode
    document.documentElement.className = "night-mode";
    document.documentElement.dataset.bsTheme = "dark";
};
