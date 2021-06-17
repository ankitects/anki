// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import CodeMirror from "codemirror/src/codemirror"

export class Codable extends HTMLTextAreaElement {
    connectedCallback(): void {
        CodeMirror.fromTextArea(this);
    }
}
