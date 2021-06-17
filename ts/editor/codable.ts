// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import CodeMirror from "codemirror/src/codemirror"

const codables: Codable[] = [];

export function toggleHtmlEdit() {
    for (const codable of codables) {
        CodeMirror.fromTextArea(codable);
    }
}

export class Codable extends HTMLTextAreaElement {
    connectedCallback(): void {
        this.setAttribute("hidden", "");
        codables.push(this);
    }
}
