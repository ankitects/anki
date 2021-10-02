// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

export const decoratedElementsKey = Symbol("decoratedElements");
export const focusInEditableKey = Symbol("focusInEditable");
export const noteEditorKey = Symbol("noteEditor");
export const editingAreaKey = Symbol("editingArea");
export const editorFieldKey = Symbol("editorField");
export const editableStylesKey = Symbol("editableStylesKey");
export const editableKey = Symbol("editableKey");
export const activeInputKey = Symbol("activeInput");

import type { Readable, Writable } from "svelte/store";
import type {
    CustomElementArray,
    DecoratedElementConstructor,
} from "../editable/decorated";
import type { NoteEditorAPI } from "./NoteEditor.svelte";
import type { EditorFieldAPI } from "./EditorField.svelte";
import type { EditingAreaAPI } from "./EditingArea.svelte";
import type { EditableAPI, EditableContextAPI } from "./Editable.svelte";
import type { CodableAPI } from "./Codable.svelte";

export type EditorContext<T extends symbol> = T extends typeof decoratedElementsKey
    ? CustomElementArray<DecoratedElementConstructor>
    : T extends typeof focusInEditableKey
    ? Readable<boolean>
    : T extends typeof noteEditorKey
    ? NoteEditorAPI
    : T extends typeof editorFieldKey
    ? EditorFieldAPI
    : T extends typeof editingAreaKey
    ? EditingAreaAPI
    : T extends typeof editableKey
    ? EditableContextAPI
    : T extends typeof activeInputKey
    ? Writable<EditableAPI | CodableAPI | null>
    : never;

import { getContext as svelteGetContext, setContext as svelteSetContext } from "svelte";

export function getContext<T extends symbol>(key: T): EditorContext<T> {
    return svelteGetContext<EditorContext<T>>(key);
}

export function setContext<T extends symbol>(
    key: T,
    value: EditorContext<T>
): EditorContext<T> {
    svelteSetContext<EditorContext<T>>(key, value);
    return value;
}
