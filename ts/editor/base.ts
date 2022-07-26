// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Code that is shared among all entry points in /ts/editor
 */
import "./legacy.css";
import "./editor-base.css";
import "../lib/runtime-require";
import "../sveltelib/export-runtime";

import * as contextKeys from "../components/context-keys";
import IconButton from "../components/IconButton.svelte";
import LabelButton from "../components/LabelButton.svelte";
import WithContext from "../components/WithContext.svelte";
import WithState from "../components/WithState.svelte";
import { ModuleName } from "../lib/i18n";
import * as editorContextKeys from "./NoteEditor.svelte";

declare global {
    interface Selection {
        modify(s: string, t: string, u: string): void;
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

export const editorModules = [
    ModuleName.EDITING,
    ModuleName.KEYBOARD,
    ModuleName.ACTIONS,
    ModuleName.BROWSING,
];

export const components = {
    IconButton,
    LabelButton,
    WithContext,
    WithState,
    contextKeys: { ...contextKeys, ...editorContextKeys },
};

export { editorToolbar } from "./editor-toolbar";
