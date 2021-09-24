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
    import { CodeMirror, htmlanki, baseOptions, gutterOptions } from "./code-mirror";

    export let parseStyle: string = "";

    const codeMirrorOptions = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const parser = new DOMParser();

    function parseHTML(html: string): string {
        const doc = parser.parseFromString(parseStyle + html, "text/html");
        return doc.body.innerHTML;
    }

    /*   onEnter(): void { */
    /*       /1* default *1/ */
    /*   } */

    /*   onPaste(): void { */
    /*       /1* default *1/ */
    /*   } */

    let codeMirror: CodeMirror.EditorFromTextArea;

    function focus(): void {
        codeMirror?.focus();
    }

    function moveCaretToEnd(): void {
        codeMirror?.setCursor(codeMirror.lineCount(), 0);
    }

    function surround(before: string, after: string): void {
        const selection = codeMirror?.getSelection();
        codeMirror?.replaceSelection(before + selection + after);
    }

    let initialRender: string = "";

    function getFieldHTML(): string {
        return codeMirror ? parseHTML(codeMirror.getValue()) : initialRender;
    }

    function setFieldHTML(content: string) {
        if (codeMirror) {
            codeMirror.setValue(content);
        } else {
            initialRender = content;
        }
    }

    const dispatch = createEventDispatcher();

    function openCodeMirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        codeMirror.on("focus", () => dispatch("codablefocus"));
        codeMirror.on(
            "change",
            (_instance: CodeMirror.Editor, change: CodeMirror.EditorChange) => {
                if (change.origin !== "setValue") {
                    dispatch("codableinput");
                }
            }
        );
        codeMirror.on("blur", () => dispatch("codableblur"));
        setFieldHTML(initialRender);
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
