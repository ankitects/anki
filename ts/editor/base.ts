// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Code that is shared among all entry points in /ts/editor
 */
import "./legacy.scss";
import "./editor-base.scss";
import "@tslib/runtime-require";
import "../sveltelib/export-runtime";

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
];

import * as contextKeys from "../components/context-keys";
import IconButton from "../components/IconButton.svelte";
import LabelButton from "../components/LabelButton.svelte";
import WithContext from "../components/WithContext.svelte";
import WithState from "../components/WithState.svelte";
import * as editorContextKeys from "./NoteEditor.svelte";

export const components = {
    IconButton,
    LabelButton,
    WithContext,
    WithState,
    contextKeys: { ...contextKeys, ...editorContextKeys },
};

export { editorToolbar } from "./editor-toolbar";
