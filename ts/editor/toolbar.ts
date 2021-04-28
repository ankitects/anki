// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import EditorToolbar from "./EditorToolbar.svelte";
import "./bootstrap.css";

export function initToolbar(i18n: Promise<void>): Promise<EditorToolbar> {
    let toolbarResolve: (value: EditorToolbar) => void;
    const toolbarPromise = new Promise<EditorToolbar>((resolve) => {
        toolbarResolve = resolve;
    });

    document.addEventListener("DOMContentLoaded", () => {
        i18n.then(() => {
            const target = document.body;
            const anchor = document.getElementById("fields")!;

            toolbarResolve(
                new EditorToolbar({
                    target,
                    anchor,
                    props: {
                        nightMode: document.documentElement.classList.contains(
                            "night-mode"
                        ),
                    },
                })
            );
        });
    });

    return toolbarPromise;
}

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "components/CommandIconButton.svelte";
