<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onDestroy, getContext } from "svelte";
    import type { Writable } from "svelte/store";
    import { CodeMirror, htmlanki, baseOptions, gutterOptions } from "./codeMirror";
    import { activeInputKey, focusInCodableKey } from "lib/context-keys";
    import type { ActiveInputAPI } from "./EditingArea.svelte";

    const codeMirrorOptions = {
        mode: htmlanki,
        ...baseOptions,
        ...gutterOptions,
    };

    const parser = new DOMParser();
    const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

    const focusInCodable = getContext<Writable<boolean>>(focusInCodableKey);

    let codeMirror: CodeMirror.EditorFromTextArea;

    function parseHTML(html: string): string {
        const doc = parser.parseFromString(`${parseStyle}${html}`, "text/html");
        return doc.body.innerHTML;
    }

    /* export class Codable extends HTMLTextAreaElement { */
    /*     codeMirror: CodeMirror.EditorFromTextArea | undefined; */

    /*     get active(): boolean { */
    /*         return Boolean(this.codeMirror); */
    /*     } */

    /*     connectedCallback(): void { */
    /*         this.setAttribute("hidden", ""); */
    /*     } */

    /*     teardown(): string { */
    /*         this.codeMirror!.toTextArea(); */
    /*         this.codeMirror = undefined; */
    /*         return this.fieldHTML; */
    /*     } */

    /*     surroundSelection(before: string, after: string): void { */
    /*         const selection = this.codeMirror!.getSelection(); */
    /*         this.codeMirror!.replaceSelection(before + selection + after); */
    /*     } */

    /*     onEnter(): void { */
    /*         /1* default *1/ */
    /*     } */

    /*     onPaste(): void { */
    /*         /1* default *1/ */
    /*     } */
    /* } */

    function focus(): void {
        codeMirror.focus();
        $focusInCodable = true;
    }

    function moveCaretToEnd(): void {
        codeMirror.setCursor(codeMirror.lineCount(), 0);
    }

    function getFieldHTML(): string {
        return parseHTML(codeMirror.getValue());
    }

    function setFieldHTML(content: string) {
        codeMirror.setValue(content);
    }

    const activeInput = getContext<Writable<ActiveInputAPI | null>>(activeInputKey);

    function openCodeMirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        $activeInput = Object.defineProperties(
            {},
            {
                name: { value: "codable" },
                focus: { value: focus },
                moveCaretToEnd: { value: moveCaretToEnd },
                fieldHTML: { get: getFieldHTML, set: setFieldHTML },
            }
        );
    }

    onDestroy(() => ($activeInput = null));
</script>

<div>
    <textarea hidden use:openCodeMirror />
</div>
