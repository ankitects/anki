// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Code that is shared among all entry points in /ts/editor
 */

import "./legacy.scss";
import "./editor-base.scss";
import "@tslib/runtime-require";
import "../sveltelib/export-runtime";

import { setupI18n } from "@tslib/i18n";
import { uiResolve } from "@tslib/ui";

import * as contextKeys from "../components/context-keys";
import IconButton from "../components/IconButton.svelte";
import LabelButton from "../components/LabelButton.svelte";
import WithContext from "../components/WithContext.svelte";
import WithState from "../components/WithState.svelte";
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

    new BrowserEditor({
        target: document.body,
        props: { uiResolve },
    });
}

async function setupNoteCreator(): Promise<void> {
    await setupI18n({ modules: editorModules });

    new NoteCreator({
        target: document.body,
        props: { uiResolve },
    });
}

async function setupReviewerEditor(): Promise<void> {
    await setupI18n({ modules: editorModules });

    new ReviewerEditor({
        target: document.body,
        props: { uiResolve },
    });
}

export function setupEditor(mode: "add" | "browse" | "review") {
    switch (mode) {
        case "add":
            setupNoteCreator();
            break;
        case "browse":
            setupBrowserEditor();
            break;
        case "review":
            setupReviewerEditor();
            break;
        default:
            alert("unexpected editor type");
    }
}
