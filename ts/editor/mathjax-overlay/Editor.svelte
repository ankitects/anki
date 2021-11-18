<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, createEventDispatcher } from "svelte";
    import { CodeMirror, latex, baseOptions } from "../code-mirror";

    export let initialValue: string;

    const codeMirrorOptions = {
        mode: latex,
        ...baseOptions,
    };

    let codeMirror: CodeMirror.EditorFromTextArea;
    const dispatch = createEventDispatcher();

    function onInput() {
        dispatch("update", { mathjax: codeMirror.getValue() });
    }

    function onBlur() {
        dispatch("codemirrorblur");
    }

    function openCodemirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        codeMirror.on("change", onInput);
        codeMirror.on("blur", onBlur);
    }

    let textarea: HTMLTextAreaElement;

    onMount(() => {
        codeMirror.focus();
        codeMirror.setCursor(codeMirror.lineCount(), 0);

        const codeMirrorElement = textarea.nextElementSibling!;
        codeMirrorElement.classList.add("mathjax-editor");
    });
</script>

<div class="mathjax-editor-container">
    <!-- TODO no focusin for now, as EditingArea will defer to Editable/Codable -->
    <textarea
        bind:this={textarea}
        value={initialValue}
        on:input={onInput}
        use:openCodemirror
    />
</div>

<style lang="scss">
    .mathjax-editor-container :global(.mathjax-editor) {
        border-radius: 0;
        border-width: 0 1px;
        border-color: var(--medium-border);

        height: auto;
        border-radius: 0 0 5px 5px;
        padding: 6px 0;
    }
</style>
