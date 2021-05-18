// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { disabledKey, nightModeKey } from "components/contextKeys";
import { writable } from "svelte/store";

import EditorToolbar from "./EditorToolbar.svelte";
import "./bootstrap.css";

const disabled = writable(false);

export function initToolbar(i18n: Promise<void>): Promise<EditorToolbar> {
    let toolbarResolve: (value: EditorToolbar) => void;
    const toolbarPromise = new Promise<EditorToolbar>((resolve) => {
        toolbarResolve = resolve;
    });

    document.addEventListener("DOMContentLoaded", () =>
        i18n.then(() => {
            const target = document.body;
            const anchor = document.getElementById("fields")!;

            const context = new Map();
            context.set(disabledKey, disabled);
            context.set(
                nightModeKey,
                document.documentElement.classList.contains("night-mode")
            );

            toolbarResolve(new EditorToolbar({ target, anchor, context } as any));
        })
    );

    return toolbarPromise;
}

export function enableButtons(): void {
    disabled.set(false);
}

export function disableButtons(): void {
    disabled.set(true);
}

export {
    updateActiveButtons,
    clearActiveButtons,
    editorToolbar,
} from "./EditorToolbar.svelte";
