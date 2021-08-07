// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import CodeMirror from "codemirror";
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/mode/stex/stex";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/fold/xml-fold";
import "codemirror/addon/edit/matchtags.js";
import "codemirror/addon/edit/closetag.js";

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

const noop = (): void => {
    /* noop */
};

export const baseOptions = {
    theme: "monokai",
    lineWrapping: true,
    matchTags: { bothTags: true },
    autoCloseTags: true,
    extraKeys: { Tab: noop, "Shift-Tab": noop },
    viewportMargin: Infinity,
    lineWiseCopyCut: false,
};

export const gutterOptions = {
    gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
    lineNumbers: true,
    foldGutter: true,
};
