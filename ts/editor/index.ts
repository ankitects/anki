// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "sveltelib/export-runtime";
import "lib/register-package";

// import type EditorToolbar from "./EditorToolbar.svelte";
// import type TagEditor from "./TagEditor.svelte";

import { filterHTML } from "html-filter";
import { setupI18n, ModuleName } from "lib/i18n";
import { isApplePlatform } from "lib/platform";
import { registerShortcut } from "lib/shortcuts";
import { bridgeCommand } from "lib/bridgecommand";
// import { updateActiveButtons } from "./toolbar";
import { saveField } from "./saving";

import "./label-container";
// import "./codable";
import "./editor-field";
import type { EditorField } from "./editor-field";
import { EditingArea } from "./editing-area";
// import "editable/editable-container";
// import "editable/editable";
import "editable/mathjax-component";

// import { initToolbar, fieldFocused } from "./toolbar";
// import { initTagEditor } from "./tag-editor";
import { getCurrentField } from "./helpers";

export { setNoteId, getNoteId } from "./note-id";
export { saveNow } from "./saving";
export { wrap, wrapIntoText } from "./wrap";
// export { editorToolbar } from "./toolbar";
export { activateStickyShortcuts } from "./label-container";
export { getCurrentField } from "./helpers";
export { components } from "./Components.svelte";

declare global {
    interface Selection {
        modify(s: string, t: string, u: string): void;
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

if (isApplePlatform()) {
    registerShortcut(() => bridgeCommand("paste"), "Control+Shift+V");
}

export const $editorToolbar = new Promise(() => {
    /* noop */
});

// export function focusField(n: number): void {
//     // const field = getEditorField(n);
//     // if (field) {
//     //     field.editingArea.focus();
//     //     field.editingArea.caretToEnd();
//     //     updateActiveButtons(new Event("manualfocus"));
//     // }
// }

export function focusIfField(x: number, y: number): boolean {
    const elements = document.elementsFromPoint(x, y);
    for (let i = 0; i < elements.length; i++) {
        const elem = elements[i] as EditingArea;
        if (elem instanceof EditingArea) {
            elem.focus();
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
    // const fieldsContainer = document.getElementById("fields")!;
    // while (fieldsContainer.childElementCount < amount) {
    //     const newField = document.createElement("div", {
    //         is: "anki-editor-field",
    //     }) as EditorField;
    //     newField.ord = fieldsContainer.childElementCount;
    //     fieldsContainer.appendChild(newField);
    // }
    // while (fieldsContainer.childElementCount > amount) {
    //     fieldsContainer.removeChild(fieldsContainer.lastElementChild as Node);
    // }
}

export function getEditorField(n: number): EditorField | null {
    // const fields = document.getElementById("fields")!.children;
    return /*(fields[n] as EditorField) ??*/ null;
}

/// forEachEditorFieldAndProvidedValue:
/// Values should be a list with the same length as the
/// number of fields. Func will be called with each field and
/// value in turn.
export function forEditorField<T>(
    values: T[],
    func: (field: EditorField, value: T) => void
): void {
    // const fields = document.getElementById("fields")!.children;
    // for (let i = 0; i < fields.length; i++) {
    //     const field = fields[i] as EditorField;
    //     func(field, values[i]);
    // }
}

// export function setFields(fields: [string, string][]): void {
//     // webengine will include the variable after enter+backspace
//     // if we don't convert it to a literal colour
//     // const color = window
//     //     .getComputedStyle(document.documentElement)
//     //     .getPropertyValue("--text-fg");
//     // adjustFieldAmount(fields.length);
//     // forEditorField(
//     //     fields,
//     //     (field: EditorField, [name, fieldContent]: [string, string]): void =>
//     //         field.initialize(name, color, fieldContent)
//     // );
//     // if (!getCurrentField()) {
//     //     // when initial focus of the window is not on editor (e.g. browser)
//     //     fieldFocused.set(false);
//     // }
// }

// export function setBackgrounds(cols: ("dupe" | "")[]): void {
//     forEditorField(cols, (field: EditorField, value: "dupe" | "") =>
//         field.editingArea.classList.toggle("dupe", value === "dupe")
//     );
//     // document
//     //     .getElementById("dupes")!
//     //     .classList.toggle("d-none", !cols.includes("dupe"));
// }

// export function setClozeHint(hint: string): void {
//     // const clozeHint = document.getElementById("cloze-hint")!;
//     // clozeHint.innerHTML = hint;
//     // clozeHint.classList.toggle("d-none", hint.length === 0);
// }

// export function setFonts(fonts: [string, number, boolean][]): void {
//     forEditorField(
//         fonts,
//         (
//             field: EditorField,
//             [fontFamily, fontSize, isRtl]: [string, number, boolean]
//         ) => {
//             field.setBaseStyling(fontFamily, `${fontSize}px`, isRtl ? "rtl" : "ltr");
//         }
//     );
// }

// export function setColorButtons([textColor, highlightColor]: [string, string]): void {
//     // $editorToolbar.then((editorToolbar) =>
//     //     (editorToolbar as any).$set({ textColor, highlightColor })
//     // );
// }

// export function setSticky(stickies: boolean[]): void {
//     forEditorField(stickies, (field: EditorField, isSticky: boolean) => {
//         field.labelContainer.activateSticky(isSticky);
//     });
// }

export function setFormat(cmd: string, arg?: string, nosave = false): void {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField(getCurrentField() as EditingArea, "key");
        // updateActiveButtons(new Event(cmd));
    }
}

// export function setTags(tags: string[]): void {
// $tagEditor.then((tagEditor: TagEditor): void => tagEditor.resetTags(tags));
// }

export const i18n = setupI18n({
    modules: [
        ModuleName.EDITING,
        ModuleName.KEYBOARD,
        ModuleName.ACTIONS,
        ModuleName.BROWSING,
    ],
});

import OldEditorAdapter from "./OldEditorAdapter.svelte";
import { nightModeKey } from "components/context-keys";

import "./editor-base.css";
import "./bootstrap.css";
import "./legacy.css";

function setupNoteEditor(i18n: Promise<void>): Promise<OldEditorAdapter> {
    let editorResolve: (value: OldEditorAdapter) => void;
    const editorPromise = new Promise<OldEditorAdapter>((resolve) => {
        editorResolve = resolve;
    });

    const context = new Map<Symbol, unknown>();

    context.set(
        nightModeKey,
        document.documentElement.classList.contains("night-mode")
    );

    i18n.then(() => {
        const noteEditor = new OldEditorAdapter({
            target: document.body,
            props: {
                class: "h-100",
            },
            context,
        } as any);

        Object.assign(globalThis, {
            setFields: noteEditor.setFields,
            setFonts: noteEditor.setFonts,
            focusField: noteEditor.focusField,
            setColorButtons: noteEditor.setColorButtons,
            setTags: noteEditor.setTags,
            setSticky: noteEditor.setSticky,
            setBackgrounds: noteEditor.setBackgrounds,
            setClozeHint: noteEditor.setClozeHint,
        });

        editorResolve(noteEditor);
    });

    return editorPromise;
}

export const noteEditorPromise = setupNoteEditor(i18n);
