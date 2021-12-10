// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./legacy.css";
import "./editor-base.css";

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "../sveltelib/export-runtime";
import "../lib/register-package";
import "../domlib/surround";

import { filterHTML } from "../html-filter";
import { execCommand } from "./helpers";
import { updateAllState } from "../components/WithState.svelte";

export function pasteHTML(
    html: string,
    internal: boolean,
    extendedMode: boolean,
): void {
    html = filterHTML(html, internal, extendedMode);

    if (html !== "") {
        setFormat("inserthtml", html);
    }
}

export function setFormat(cmd: string, arg?: string, _nosave = false): void {
    execCommand(cmd, false, arg);
    updateAllState(new Event(cmd));
}

import { setupI18n, ModuleName } from "../lib/i18n";
import { isApplePlatform } from "../lib/platform";
import { registerShortcut } from "../lib/shortcuts";
import { bridgeCommand } from "../lib/bridgecommand";

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
import type { NoteEditorAPI } from "./OldEditorAdapter.svelte";

async function setupNoteEditor(): Promise<NoteEditorAPI> {
    await i18n;

    const api: Partial<NoteEditorAPI> = {};

    const noteEditor = new OldEditorAdapter({
        target: document.body,
        props: { api: api as NoteEditorAPI },
    });

    Object.assign(globalThis, {
        setField: noteEditor.setField,
        setFields: noteEditor.setFields,
        setDescriptions: noteEditor.setDescriptions,
        setFonts: noteEditor.setFonts,
        focusField: noteEditor.focusField,
        setColorButtons: noteEditor.setColorButtons,
        setTags: noteEditor.setTags,
        setSticky: noteEditor.setSticky,
        setBackgrounds: noteEditor.setBackgrounds,
        setClozeHint: noteEditor.setClozeHint,
        saveNow: noteEditor.saveFieldNow,
        activateStickyShortcuts: noteEditor.activateStickyShortcuts,
        focusIfField: noteEditor.focusIfField,
        setNoteId: noteEditor.setNoteId,
    });

    return api as NoteEditorAPI;
}

export const noteEditorPromise = setupNoteEditor();
export { editorToolbar } from "./EditorToolbar.svelte";
