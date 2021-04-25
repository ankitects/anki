// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem, IterableToolbarItem } from "sveltelib/types";

import EditorToolbar from "./EditorToolbar.svelte";
export { default as EditorToolbar } from "./EditorToolbar.svelte";

import "./bootstrap.css";

export function editorToolbar(
    target: HTMLElement,
    buttons: IterableToolbarItem[] = [],
    menus: ToolbarItem[] = []
): EditorToolbar {
    return new EditorToolbar({
        target,
        props: {
            buttons,
            menus,
            nightMode: document.documentElement.classList.contains("night-mode"),
        },
    });
}

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
