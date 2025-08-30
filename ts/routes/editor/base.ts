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

import NoteEditor, * as editorContextKeys from "./NoteEditor.svelte";

declare global {
    interface Selection {
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

import { modalsKey } from "$lib/components/context-keys";
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
    ModuleName.ADDING,
    ModuleName.QT_MISC,
    ModuleName.DECKS,
];

export const components = {
    IconButton,
    LabelButton,
    WithContext,
    WithState,
    contextKeys: { ...contextKeys, ...editorContextKeys },
};

export { editorToolbar } from "./editor-toolbar";

export async function setupEditor(mode: EditorMode, isLegacy = false) {
    if (!["add", "browser", "current"].includes(mode)) {
        alert("unexpected editor type");
        return;
    }
    const context = new Map();
    context.set(modalsKey, new Map());
    await setupI18n({ modules: editorModules });
    mount(NoteEditor, { target: document.body, props: { uiResolve, mode, isLegacy }, context });
}
