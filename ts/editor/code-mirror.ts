// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "codemirror/lib/codemirror.css";
import "codemirror/theme/monokai.css";
import "codemirror/addon/fold/foldgutter.css";

import CodeMirror from "codemirror";
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/mode/stex/stex";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/fold/xml-fold";
import "codemirror/addon/edit/matchtags";
import "codemirror/addon/edit/closetag";
import "codemirror/addon/display/placeholder";

export { CodeMirror };

export const latex = {
    name: "stex",
    inMathMode: true,
};

export const htmlanki = {
    name: "htmlmixed",
    tags: {
        "anki-mathjax": [[null, null, latex]],
    },
};

export const baseOptions: CodeMirror.EditorConfiguration = {
    theme: "monokai",
    lineWrapping: true,
    matchTags: { bothTags: true },
    autoCloseTags: true,
    extraKeys: { Tab: false, "Shift-Tab": false },
    tabindex: 0,
    viewportMargin: Infinity,
    lineWiseCopyCut: false,
};

export const gutterOptions: CodeMirror.EditorConfiguration = {
    gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
    lineNumbers: true,
    foldGutter: true,
};
