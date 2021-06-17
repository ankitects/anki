// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as CodeMirror from "codemirror/lib/codemirror";
import "codemirror/mode/htmlmixed/htmlmixed";

const codeMirrorOptions = {
    lineNumbers: true,
    lineWrapping: true,
    mode: "htmlmixed",
    theme: "monokai",
};

const parser = new DOMParser();

export class Codable extends HTMLTextAreaElement {
    codeMirror: CodeMirror | undefined;
    active: boolean;

    constructor() {
        super();
        this.active = false;
    }

    connectedCallback(): void {
        this.setAttribute("hidden", "");
    }

    toggle(html: string): string {
        return this.codeMirror ? this.teardown() : this.setup(html);
    }

    setup(html: string): string {
        this.active = true;
        this.value = html;
        this.codeMirror = CodeMirror.fromTextArea(this, codeMirrorOptions);
        return "";
    }

    teardown(): string {
        this.active = false;
        this.codeMirror.toTextArea();
        this.codeMirror = undefined;

        const doc = parser.parseFromString(this.value, "text/html");
        return doc.documentElement.innerHTML;
    }
}
