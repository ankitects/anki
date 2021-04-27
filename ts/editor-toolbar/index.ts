// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem, IterableToolbarItem } from "./types";

import EditorToolbar from "./EditorToolbar.svelte";
export { default as EditorToolbar } from "./EditorToolbar.svelte";

import "./bootstrap.css";

interface EditorToolbarArgs {
    target: HTMLElement;
    anchor?: HTMLElement;
    buttons?: IterableToolbarItem[];
    menus?: ToolbarItem[];
}

export function editorToolbar({
    target,
    anchor = undefined,
    buttons = [],
    menus = [],
}: EditorToolbarArgs): EditorToolbar {
    return new EditorToolbar({
        target,
        anchor,
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
