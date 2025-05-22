// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Code that is shared among all entry points in /ts/editor
 */

import "./legacy.scss";
import "./editor-base.scss";
import "@tslib/runtime-require";
import "$lib/sveltelib/export-runtime";

import { setupI18n } from "@tslib/i18n";
import { uiResolve } from "@tslib/ui";

import * as contextKeys from "$lib/components/context-keys";
import IconButton from "$lib/components/IconButton.svelte";
import LabelButton from "$lib/components/LabelButton.svelte";
import WithContext from "$lib/components/WithContext.svelte";
import WithState from "$lib/components/WithState.svelte";

import BrowserEditor from "./BrowserEditor.svelte";
import NoteCreator from "./NoteCreator.svelte";
import * as editorContextKeys from "./NoteEditor.svelte";
import ReviewerEditor from "./ReviewerEditor.svelte";

declare global {
    interface Selection {
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

import { ModuleName } from "@tslib/i18n";
import { mount } from "svelte";
import type { EditorMode } from "./types";

export const editorModules = [
    ModuleName.EDITING,
    ModuleName.KEYBOARD,
    ModuleName.ACTIONS,
    ModuleName.BROWSING,
    ModuleName.NOTETYPES,
    ModuleName.IMPORTING,
    ModuleName.UNDO,
];

export const components = {
    IconButton,
    LabelButton,
    WithContext,
    WithState,
    contextKeys: { ...contextKeys, ...editorContextKeys },
};

export { editorToolbar } from "./editor-toolbar";

async function setupBrowserEditor(): Promise<void> {
    await setupI18n({ modules: editorModules });
    mount(BrowserEditor, { target: document.body, props: { uiResolve } });
}

async function setupNoteCreator(): Promise<void> {
    await setupI18n({ modules: editorModules });
    mount(NoteCreator, { target: document.body, props: { uiResolve } });
}

async function setupReviewerEditor(): Promise<void> {
    await setupI18n({ modules: editorModules });
    mount(ReviewerEditor, { target: document.body, props: { uiResolve } });
}

export async function setupEditor(mode: EditorMode) {
    switch (mode) {
        case "add":
            await setupNoteCreator();
            break;
        case "browse":
            await setupBrowserEditor();
            break;
        case "review":
            await setupReviewerEditor();
            break;
        default:
            alert("unexpected editor type");
    }
}
