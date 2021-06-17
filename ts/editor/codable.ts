// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import CodeMirror from "codemirror/src/codemirror"

const codeMirrorOptions = {
    lineNumbers: true,
    mode: "htmlmixed",
}

export class Codable extends HTMLTextAreaElement {
    codeMirror: any;

    connectedCallback(): void {
        this.setAttribute("hidden", "");
    }

    toggle(html: string): string {
        return this.codeMirror ?  this.teardown() : this.setup(html);
    }

    setup(html: string): string {
        this.innerHTML = html;
        this.codeMirror = CodeMirror.fromTextArea(this, codeMirrorOptions);
        return html;
    }

    teardown(): string {
        this.codeMirror.toTextArea();
        this.codeMirror = undefined;
        console.log(this.innerHTML)
        return this.innerHTML;
    }
}
