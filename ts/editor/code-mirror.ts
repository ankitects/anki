// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Extension } from "@codemirror/state";
import { EditorView } from "@codemirror/view";

import type { Readable } from "svelte/store";

import storeSubscribe from "../sveltelib/store-subscribe";

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

export function focusAndSetCaret(
    editor: EditorView,
    position: CodeMirror.Position = { line: editor.lineCount(), ch: 0 },
): void {
    editor.focus();
    editor.setCursor(position);
}

interface OpenCodeMirrorOptions {
    configuration: Extension;
    resolve(editor: EditorView): void;
    hidden: boolean;
}

export function openCodeMirror(
    textarea: HTMLTextAreaElement,
    options: OpenCodeMirrorOptions,
): { update: (options: OpenCodeMirrorOptions) => void; destroy: () => void } {
    let editor: EditorView | null = null;

    function update({
        configuration,
        resolve,
        hidden,
    }: OpenCodeMirrorOptions): void {
        if (editor) {
            // for (const key in configuration) {
            //     editor.setOption(
            //         key as keyof CodeMirror.EditorConfiguration,
            //         configuration[key],
            //     );
            // }
        } else if (!hidden) {
            editor = editorFromTextArea(textarea, configuration);
            resolve?.(editor);
        }
    }

    update(options);

    return {
        update,
        destroy(): void {
            if (editor) editorToTextArea(textarea, editor);
            editor = null;
        },
    };
}

function editorFromTextArea(textarea, extensions: Extension) {
    let view = new EditorView({ doc: textarea.value, extensions });
    textarea.parentNode.insertBefore(view.dom, textarea);
    textarea.style.display = "none";
    return view;
}

function editorToTextArea(textarea, view: EditorView) {
    textarea.style.display = "block";
    view.destroy();
}

/**
 * Sets up the contract with the code store and location restoration.
 */
export function setupCodeMirror(
    editor: EditorView,
    code: Readable<string>,
): void {
    const { subscribe, unsubscribe } = storeSubscribe(
        code,
        (value: string): void => {
            console.log(value);
            editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value } });
        },
        false,
    );

    // // TODO passing in the tabindex option does not do anything: bug?
    // editor.getInputField().tabIndex = 0;

    // let ranges: CodeMirror.Range[] | null = null;

    // editor.on("focus", () => {
    //     if (ranges) {
    //         try {
    //             editor.setSelections(ranges);
    //         } catch {
    //             ranges = null;
    //             editor.setCursor(editor.lineCount(), 0);
    //         }
    //     }
    //     unsubscribe();
    // });

    // editor.on("mousedown", () => {
    //     // Prevent focus restoring location
    //     ranges = null;
    // });

    // editor.on("blur", () => {
    //     ranges = editor.listSelections();
    //     subscribe();
    // });

    subscribe();
}
