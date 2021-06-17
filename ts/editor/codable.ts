// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as CodeMirror from "codemirror/lib/codemirror";
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/fold/xml-fold";
import "codemirror/addon/edit/matchtags.js";

const codeMirrorOptions = {
    mode: "htmlmixed",
    theme: "monokai",
    lineNumbers: true,
    lineWrapping: true,
    foldGutter: true,
    gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
    matchTags: { bothTags: true },
    viewportMargin: Infinity,
};

const parser = new DOMParser();

function parseHTML(html: string): string {
    const doc = parser.parseFromString(html, "text/html");
    return doc.documentElement.innerHTML;
}

export class Codable extends HTMLTextAreaElement {
    codeMirror: CodeMirror | undefined;
    active: boolean;

    constructor() {
        super();
        this.active = false;
    }

    set fieldHTML(content: string) {
        this.value = content;
    }

    get fieldHTML(): string {
        return parseHTML(this.codeMirror.getValue());
    }

    connectedCallback(): void {
        this.setAttribute("hidden", "");
    }

    setup(html: string): void {
        this.active = true;
        this.fieldHTML = html;
        this.codeMirror = CodeMirror.fromTextArea(this, codeMirrorOptions);
    }

    focus(): void {
        this.codeMirror.focus();
    }

    caretToEnd(): void {
        this.codeMirror.setCursor(this.codeMirror.lineCount(), 0);
    }

    teardown(): string {
        this.active = false;
        this.codeMirror.toTextArea();
        this.codeMirror = undefined;
        return parseHTML(this.value);
    }
}
