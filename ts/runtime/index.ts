// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint-disable @typescript-eslint/no-var-requires */

import IconButton from "../components/IconButton.svelte";
import WithState from "../components/WithState.svelte";
import type { NoteEditorPackage } from "../editor/NoteEditor.svelte";
import { registerPackage } from "../lib/runtime-require";

// The typed version from runtime-require will need adjusting if we go
// down this route; for now just use an untyped version.
declare function require(text: string): unknown;

/**
 * Exports from the editing screen. Only available on pages that show an editor.
 */
export const editor = require("anki/NoteEditor") as NoteEditorPackage;

/**
 * Basic Svelte UI components. Available on all pages.
 */
export const components = {
    IconButton,
    WithState,
};

registerPackage("anki/runtime", {
    editor,
    components,
});
