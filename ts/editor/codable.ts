// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as CodeMirror from "codemirror/lib/codemirror";
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/mode/stex/stex";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/fold/xml-fold";
import "codemirror/addon/edit/matchtags.js";
import "codemirror/addon/edit/closetag.js";

import { inCodable } from "./toolbar";

const latex = {
    name: "stex",
    inMathMode: true,
};

const htmlanki = {
    name: "htmlmixed",
    tags: {
        "anki-mathjax": [[null, null, latex]],
    },
};

const codeMirrorOptions = {
    mode: htmlanki,
    theme: "monokai",
    lineNumbers: true,
    lineWrapping: true,
    foldGutter: true,
    gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
    matchTags: { bothTags: true },
    autoCloseTags: true,
    extraKeys: { Tab: false, "Shift-Tab": false },
    viewportMargin: Infinity,
    lineWiseCopyCut: false,
};

const parser = new DOMParser();
const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

function parseHTML(html: string): string {
    const doc = parser.parseFromString(`${parseStyle}${html}`, "text/html");
    return doc.body.innerHTML;
}

export class Codable extends HTMLTextAreaElement {
    codeMirror: CodeMirror | undefined;

    get active(): boolean {
        return Boolean(this.codeMirror);
    }

    set fieldHTML(content: string) {
        if (this.active) {
            this.codeMirror.setValue(content);
        } else {
            this.value = content;
        }
    }

    get fieldHTML(): string {
        return parseHTML(this.active ? this.codeMirror.getValue() : this.value);
    }

    connectedCallback(): void {
        this.setAttribute("hidden", "");
    }

    setup(html: string): void {
        this.fieldHTML = html;
        this.codeMirror = CodeMirror.fromTextArea(this, codeMirrorOptions);
    }

    teardown(): string {
        this.codeMirror.toTextArea();
        this.codeMirror = undefined;
        return this.fieldHTML;
    }

    focus(): void {
        this.codeMirror.focus();
        inCodable.set(true);
    }

    caretToEnd(): void {
        this.codeMirror.setCursor(this.codeMirror.lineCount(), 0);
    }

    surroundSelection(before: string, after: string): void {
        const selection = this.codeMirror.getSelection();
        this.codeMirror.replaceSelection(before + selection + after);
    }

    onEnter(): void {
        /* default */
    }

    onPaste(): void {
        /* default */
    }
}

customElements.define("anki-codable", Codable, { extends: "textarea" });
