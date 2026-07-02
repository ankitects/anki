// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";

/**
 * Indicates whether an IME composition session is currently active
 */
export const isComposing = writable(false);

window.addEventListener("compositionstart", () => isComposing.set(true));
window.addEventListener("compositionend", () => isComposing.set(false));
