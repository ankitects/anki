// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

export const decoratedElementsKey = Symbol("decoratedElements");
export const editableStylesKey = Symbol("editableStylesKey");
export const editableKey = Symbol("editableKey");

import type {
    CustomElementArray,
    DecoratedElementConstructor,
} from "../editable/decorated";
import type { EditableContextAPI } from "./Editable.svelte";

export type EditorContext<T extends symbol> = T extends typeof decoratedElementsKey
    ? CustomElementArray<DecoratedElementConstructor>
    : T extends typeof editableKey
    ? EditableContextAPI
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
