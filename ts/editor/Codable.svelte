<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface CodableAPI {
        readonly name: string;
        focus(): void;
        moveCaretToEnd(): void;
        fieldHTML: string;
    }
</script>

<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import { CodeMirror, htmlanki, baseOptions, gutterOptions } from "./codeMirror";

    const codeMirrorOptions = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const parser = new DOMParser();

    /* TODO this should also be moved elsewhere + made configurable */
    const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

    let codeMirror: CodeMirror.EditorFromTextArea;

    function parseHTML(html: string): string {
        const doc = parser.parseFromString(`${parseStyle}${html}`, "text/html");
        return doc.body.innerHTML;
    }

    /*   onEnter(): void { */
    /*       /1* default *1/ */
    /*   } */

    /*   onPaste(): void { */
    /*       /1* default *1/ */
    /*   } */

    function focus(): void {
        codeMirror.focus();
    }

    function moveCaretToEnd(): void {
        codeMirror.setCursor(codeMirror.lineCount(), 0);
    }

    function surround(before: string, after: string): void {
        const selection = codeMirror.getSelection();
        codeMirror.replaceSelection(before + selection + after);
    }

    function getFieldHTML(): string {
        return parseHTML(codeMirror.getValue());
    }

    function setFieldHTML(content: string) {
        codeMirror.setValue(content);
    }

    const dispatch = createEventDispatcher();

    function openCodeMirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        codeMirror.on("focus", () => dispatch("codablefocus"));
        codeMirror.on("changes", () => dispatch("codableinput"));
        codeMirror.on("blur", () => dispatch("codableblur"));
    }

    export const api: CodableAPI = Object.defineProperties(
        {},
        {
            name: { value: "codable" },
            focus: { value: focus },
            moveCaretToEnd: { value: moveCaretToEnd },
            surround: { value: surround },
            fieldHTML: { get: getFieldHTML, set: setFieldHTML },
            codeMirror: { get: () => codeMirror },
        }
    );
</script>

<div>
    <textarea hidden use:openCodeMirror />
</div>
