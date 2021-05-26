// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { filterHTML } from "html-filter";
import { updateActiveButtons } from "./toolbar";
import { setupI18n, ModuleName } from "lib/i18n";

import "./fields.css";

import { saveField } from "./changeTimer";

import { EditorField } from "./editorField";
import { LabelContainer } from "./labelContainer";
import { EditingArea } from "./editingArea";
import { FieldsContainer, getCurrentField } from "./fieldsContainer";
import { Editable } from "./editable";
import { initToolbar } from "./toolbar";

export { setNoteId, getNoteId } from "./noteId";
export { saveNow } from "./changeTimer";
export { wrap, wrapIntoText } from "./wrap";
export { editorToolbar } from "./toolbar";
export { getCurrentField } from "./fieldsContainer";

declare global {
    interface Selection {
        modify(s: string, t: string, u: string): void;
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

customElements.define("anki-editable", Editable);
customElements.define("anki-editing-area", EditingArea, { extends: "div" });
customElements.define("anki-label-container", LabelContainer, { extends: "div" });
customElements.define("anki-editor-field", EditorField, { extends: "div" });
customElements.define("anki-fields-container", FieldsContainer, { extends: "div" });

export function focusField(n: number): void {
    getFieldsContainer().focusField(n);
}

export function focusIfField(x: number, y: number): boolean {
    const elements = document.elementsFromPoint(x, y);
    for (let i = 0; i < elements.length; i++) {
        const elem = elements[i] as EditingArea;
        if (elem instanceof EditingArea) {
            elem.focusEditable();
            return true;
        }
    }
    return false;
}

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

export function getEditorField(n: number): EditorField | null {
    return getFieldsContainer().getEditorField(n);
}

function getFieldsContainer(): FieldsContainer {
    return document.getElementById("fields")! as FieldsContainer;
}

/// forEachEditorFieldAndProvidedValue:
/// Values should be a list with the same length as the
/// number of fields. Func will be called with each field and
/// value in turn.
export function forEditorField<T>(
    values: T[],
    func: (field: EditorField, value: T) => void
): void {
    getFieldsContainer().forEditorField(values, func);
}

export function setFields(fields: [string, string][]): void {
    // webengine will include the variable after enter+backspace
    // if we don't convert it to a literal colour
    getFieldsContainer().setFields(fields);
}

export function setBackgrounds(cols: ("dupe" | "")[]): void {
    forEditorField(cols, (field: EditorField, value: "dupe" | "") =>
        field.editingArea.classList.toggle("dupe", value === "dupe")
    );
    document
        .getElementById("dupes")!
        .classList.toggle("is-inactive", !cols.includes("dupe"));
}

export function setFonts(fonts: [string, number, boolean][]): void {
    forEditorField(
        fonts,
        (
            field: EditorField,
            [fontFamily, fontSize, isRtl]: [string, number, boolean]
        ) => {
            field.setBaseStyling(fontFamily, `${fontSize}px`, isRtl ? "rtl" : "ltr");
        }
    );
}

export function setSticky(stickies: boolean[]): void {
    getFieldsContainer().setSticky(stickies);
}

export function setFormat(cmd: string, arg?: string, nosave = false): void {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField(getCurrentField() as EditingArea, "key");
        updateActiveButtons(new Event(cmd));
    }
}

const i18n = setupI18n({
    modules: [
        ModuleName.EDITING,
        ModuleName.KEYBOARD,
        ModuleName.ACTIONS,
        ModuleName.BROWSING,
    ],
});

import type EditorToolbar from "./EditorToolbar.svelte";

export const $editorToolbar: Promise<EditorToolbar> = initToolbar(i18n);
