// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { filterHTML } from "html-filter";
import { updateActiveButtons, disableButtons } from "./toolbar";
import { setupI18n, ModuleName } from "lib/i18n";

import "./fields.css";

import { caretToEnd } from "./helpers";
import { saveField } from "./changeTimer";

import { EditorField } from "./editorField";
import { LabelContainer } from "./labelContainer";
import { EditingArea } from "./editingArea";
import { Editable } from "./editable";
import { initToolbar } from "./toolbar";

export { setNoteId, getNoteId } from "./noteId";
export { saveNow } from "./changeTimer";
export { wrap, wrapIntoText } from "./wrap";
export { editorToolbar } from "./toolbar";

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

export function getCurrentField(): EditingArea | null {
    return document.activeElement instanceof EditingArea
        ? document.activeElement
        : null;
}

export function focusField(n: number): void {
    const field = getEditorField(n);

    if (field) {
        field.editingArea.focusEditable();
        caretToEnd(field.editingArea);
        updateActiveButtons(new Event("manualfocus"));
    }
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

function adjustFieldAmount(amount: number): void {
    const fieldsContainer = document.getElementById("fields")!;

    while (fieldsContainer.childElementCount < amount) {
        const newField = document.createElement("div", {
            is: "anki-editor-field",
        }) as EditorField;
        newField.ord = fieldsContainer.childElementCount;
        fieldsContainer.appendChild(newField);
    }

    while (fieldsContainer.childElementCount > amount) {
        fieldsContainer.removeChild(fieldsContainer.lastElementChild as Node);
    }
}

export function getEditorField(n: number): EditorField | null {
    const fields = document.getElementById("fields")!.children;
    return (fields[n] as EditorField) ?? null;
}

/// forEachEditorFieldAndProvidedValue:
/// Values should be a list with the same length as the
/// number of fields. Func will be called with each field and
/// value in turn.
export function forEditorField<T>(
    values: T[],
    func: (field: EditorField, value: T) => void
): void {
    const fields = document.getElementById("fields")!.children;
    for (let i = 0; i < fields.length; i++) {
        const field = fields[i] as EditorField;
        func(field, values[i]);
    }
}

export function setFields(fields: [string, string][]): void {
    // webengine will include the variable after enter+backspace
    // if we don't convert it to a literal colour
    const color = window
        .getComputedStyle(document.documentElement)
        .getPropertyValue("--text-fg");

    adjustFieldAmount(fields.length);
    forEditorField(
        fields,
        (field: EditorField, [name, fieldContent]: [string, string]): void =>
            field.initialize(name, color, fieldContent)
    );

    if (!getCurrentField()) {
        // when initial focus of the window is not on editor (e.g. browser)
        disableButtons();
    }
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
    forEditorField(stickies, (field: EditorField, isSticky: boolean) => {
        field.labelContainer.activateSticky(isSticky);
    });
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
