// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
*/

import { CodeMirror, htmlanki, baseOptions, gutterOptions } from "./codeMirror";
// import { inCodable } from "./toolbar";

const codeMirrorOptions = {
    mode: htmlanki,
    ...baseOptions,
    ...gutterOptions,
};

const parser = new DOMParser();
const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

function parseHTML(html: string): string {
    const doc = parser.parseFromString(`${parseStyle}${html}`, "text/html");
    return doc.body.innerHTML;
}

export class Codable extends HTMLTextAreaElement {
    codeMirror: CodeMirror.EditorFromTextArea | undefined;

    get active(): boolean {
        return Boolean(this.codeMirror);
    }

    set fieldHTML(content: string) {
        if (this.active) {
            this.codeMirror?.setValue(content);
        } else {
            this.value = content;
        }
    }

    get fieldHTML(): string {
        return parseHTML(this.active ? this.codeMirror!.getValue() : this.value);
    }

    connectedCallback(): void {
        this.setAttribute("hidden", "");
    }

    setup(html: string): void {
        this.fieldHTML = html;
        this.codeMirror = CodeMirror.fromTextArea(this, codeMirrorOptions);
        // this.codeMirror.on("blur", () => inCodable.set(false));
    }

    teardown(): string {
        this.codeMirror!.toTextArea();
        this.codeMirror = undefined;
        return this.fieldHTML;
    }

    focus(): void {
        this.codeMirror!.focus();
        // inCodable.set(true);
    }

    caretToEnd(): void {
        this.codeMirror!.setCursor(this.codeMirror!.lineCount(), 0);
    }

    surroundSelection(before: string, after: string): void {
        const selection = this.codeMirror!.getSelection();
        this.codeMirror!.replaceSelection(before + selection + after);
    }

    onEnter(): void {
        /* default */
    }

    onPaste(): void {
        /* default */
    }
}

customElements.define("anki-codable", Codable, { extends: "textarea" });
