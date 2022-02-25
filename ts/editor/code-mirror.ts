// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "codemirror/lib/codemirror.css";
import "codemirror/addon/fold/foldgutter.css";
import "codemirror/theme/monokai.css";
import "codemirror/mode/htmlmixed/htmlmixed";
import "codemirror/mode/stex/stex";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/fold/xml-fold";
import "codemirror/addon/edit/matchtags";
import "codemirror/addon/edit/closetag";
import "codemirror/addon/display/placeholder";

import CodeMirror from "codemirror";
import type { Readable } from "svelte/store";

import storeSubscribe from "../sveltelib/store-subscribe";

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

export const lightTheme = "default";
export const darkTheme = "monokai";

export const baseOptions: CodeMirror.EditorConfiguration = {
    theme: lightTheme,
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

export function focusAndCaretAfter(editor: CodeMirror.Editor): void {
    editor.focus();
    editor.setCursor(editor.lineCount(), 0);
}

interface OpenCodeMirrorOptions {
    configuration: CodeMirror.EditorConfiguration;
    resolve(editor: CodeMirror.EditorFromTextArea): void;
}

export function openCodeMirror(
    textarea: HTMLTextAreaElement,
    { configuration, resolve }: Partial<OpenCodeMirrorOptions>,
): { update: (options: Partial<OpenCodeMirrorOptions>) => void; destroy: () => void } {
    const editor = CodeMirror.fromTextArea(textarea, configuration);
    resolve?.(editor);

    return {
        update({ configuration }: Partial<OpenCodeMirrorOptions>): void {
            for (const key in configuration) {
                editor.setOption(
                    key as keyof CodeMirror.EditorConfiguration,
                    configuration[key],
                );
            }
        },
        destroy(): void {
            editor.toTextArea();
        },
    };
}

/**
 * Sets up the contract with the code store and location restoration.
 */
export function setupCodeMirror(
    editor: CodeMirror.Editor,
    code: Readable<string>,
): void {
    const { subscribe, unsubscribe } = storeSubscribe(
        code,
        (value: string): void => editor.setValue(value),
        false,
    );

    // TODO passing in the tabindex option does not do anything: bug?
    editor.getInputField().tabIndex = 0;

    let ranges: CodeMirror.Range[] | null = null;

    editor.on("focus", () => {
        if (ranges) {
            try {
                editor.setSelections(ranges);
            } catch {
                ranges = null;
                editor.setCursor(editor.lineCount(), 0);
            }
        }
        unsubscribe();
    });

    editor.on("mousedown", () => {
        // Prevent focus restoring location
        ranges = null;
    });

    editor.on("blur", () => {
        ranges = editor.listSelections();
        subscribe();
    });

    subscribe();
}
