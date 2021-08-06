<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import * as CodeMirror from "codemirror/lib/codemirror";
    import "codemirror/mode/stex/stex";
    import "codemirror/addon/fold/foldcode";
    import "codemirror/addon/fold/foldgutter";
    import "codemirror/addon/fold/xml-fold";
    import "codemirror/addon/edit/matchtags.js";
    import "codemirror/addon/edit/closetag.js";

    export let initialValue: string;

    const latex = {
        name: "stex",
        inMathMode: true,
    };

    const codeMirrorOptions = {
        mode: latex,
        theme: "monokai",
        /* lineNumbers: true, */
        /* lineWrapping: true, */
        /* foldGutter: true, */
        /* gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"], */
        /* matchTags: { bothTags: true }, */
        /* extraKeys: { Tab: false, "Shift-Tab": false }, */
        /* viewportMargin: Infinity, */
        /* lineWiseCopyCut: false, */
        /* autofocus: true, */
    };

    let codeMirror: CodeMirror;

    function openCodemirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        codeMirror.setCursor(codeMirror.lineCount(), 0);
    }

    function autofocus(textarea: HTMLTextAreaElement): void {
        textarea.focus();
    }
</script>

<!-- <textarea bind:value use:openCodemirror /> -->
<div on:click|stopPropagation on:focus|stopPropagation on:keydown|stopPropagation>
    <!-- TODO no focusin for now, as EditingArea will defer to Editable/Codable -->
    <textarea
        value={initialValue}
        on:mouseup|preventDefault|stopPropagation
        on:click|stopPropagation
        on:focusin|stopPropagation
        use:autofocus
    />
</div>
