// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { caretToEnd } from "./helpers";
import type { EditorField } from "./editorField";
import { EditingArea } from "./editingArea";
import { updateActiveButtons, disableButtons } from "./toolbar";

export function getCurrentField(): EditingArea | null {
    return document.activeElement instanceof EditingArea
        ? document.activeElement
        : null;
}

export class FieldsContainer extends HTMLDivElement {
    fields: Array<EditorField>;

    constructor() {
        super();
        this.fields = [];
    }

    focusField(n: number): void {
        const field = this.getEditorField(n);

        if (field) {
            field.editingArea.focusEditable();
            caretToEnd(field.editingArea);
            updateActiveButtons(new Event("manualfocus"));
        }
    }

    adjustFieldAmount(amount: number): void {
        while (this.fields.length < amount) {
            const newField = document.createElement("div", {
                is: "anki-editor-field",
            }) as EditorField;
            newField.ord = this.fields.length;
            this.appendChild(newField);
            this.fields.push(newField);
        }

        while (this.fields.length > amount) {
            const lastElement = this.fields.pop() as EditorField;
            this.removeChild(lastElement);
        }
    }

    getEditorField(n: number): EditorField | null {
        return (this.fields[n] as EditorField) ?? null;
    }

    forEditorField<T>(values: T[], func: (field: EditorField, value: T) => void): void {
        for (let i = 0; i < this.fields.length; i++) {
            const field = this.fields[i];
            func(field, values[i]);
        }
    }

    setFields(fields: [string, string][]): void {
        // webengine will include the variable after enter+backspace
        // if we don't convert it to a literal colour
        const color = window
            .getComputedStyle(document.documentElement)
            .getPropertyValue("--text-fg");

        this.adjustFieldAmount(fields.length);
        this.forEditorField(
            fields,
            (field: EditorField, [name, fieldContent]: [string, string]): void =>
                field.initialize(name, color, fieldContent)
        );

        if (!getCurrentField()) {
            // when initial focus of the window is not on editor (e.g. browser)
            disableButtons();
        }
    }

    setSticky(stickies: boolean[]): void {
        this.forEditorField(stickies, (field: EditorField, isSticky: boolean) => {
            field.labelContainer.activateSticky(isSticky);
        });
    }
}
