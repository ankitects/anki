// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

export { setNoteId } from "./note-id";
export const $editorToolbar = new Promise(() => {
    /* noop */
});

import { filterHTML } from "html-filter";

export function pasteHTML(
    html: string,
    internal: boolean,
    extendedMode: boolean
): void {
    html = filterHTML(html, internal, extendedMode);

    if (html !== "") {
        setFormat("inserthtml", html);
    }
}

export function setFormat(cmd: string, arg?: string, _nosave = false): void {
    document.execCommand(cmd, false, arg);
    // TODO ... maybe we also need to copy/paste/cut code entirely to JS
    // if (!nosave) {
    //     saveField(getCurrentField() as EditingArea, "key");
    //     updateActiveButtons(new Event(cmd));
    // }
}

import "sveltelib/export-runtime";
import "lib/register-package";

import { setupI18n, ModuleName } from "lib/i18n";
import { isApplePlatform } from "lib/platform";
import { registerShortcut } from "lib/shortcuts";
import { bridgeCommand } from "lib/bridgecommand";

declare global {
    interface Selection {
        modify(s: string, t: string, u: string): void;
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

if (isApplePlatform()) {
    registerShortcut(() => bridgeCommand("paste"), "Control+Shift+V");
}

export const i18n = setupI18n({
    modules: [
        ModuleName.EDITING,
        ModuleName.KEYBOARD,
        ModuleName.ACTIONS,
        ModuleName.BROWSING,
    ],
});

import OldEditorAdapter from "./OldEditorAdapter.svelte";
import { nightModeKey } from "components/context-keys";

import "./editor-base.css";
import "./bootstrap.css";
import "./legacy.css";

function setupNoteEditor(i18n: Promise<void>): Promise<OldEditorAdapter> {
    let editorResolve: (value: OldEditorAdapter) => void;
    const editorPromise = new Promise<OldEditorAdapter>((resolve) => {
        editorResolve = resolve;
    });

    const context = new Map<symbol, unknown>();

    context.set(
        nightModeKey,
        document.documentElement.classList.contains("night-mode")
    );

    i18n.then(() => {
        const noteEditor = new OldEditorAdapter({
            target: document.body,
            props: {
                class: "h-100",
            },
            context,
        } as any);

        Object.assign(globalThis, {
            setFields: noteEditor.setFields,
            setFonts: noteEditor.setFonts,
            focusField: noteEditor.focusField,
            setColorButtons: noteEditor.setColorButtons,
            setTags: noteEditor.setTags,
            setSticky: noteEditor.setSticky,
            setBackgrounds: noteEditor.setBackgrounds,
            setClozeHint: noteEditor.setClozeHint,
        });

        editorResolve(noteEditor);
    });

    return editorPromise;
}

export const noteEditorPromise = setupNoteEditor(i18n);
