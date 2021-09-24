<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher } from "svelte";
    import { ChangeTimer } from "../change-timer";
    import { CodeMirror, latex, baseOptions } from "../code-mirror";

    export let initialValue: string;

    const codeMirrorOptions = {
        mode: latex,
        ...baseOptions,
    };

    let codeMirror: CodeMirror.EditorFromTextArea;
    const changeTimer = new ChangeTimer();
    const dispatch = createEventDispatcher();

    function onInput() {
        changeTimer.schedule(
            () => dispatch("update", { mathjax: codeMirror.getValue() }),
            400
        );
    }

    function onBlur() {
        changeTimer.fireImmediately();
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

<div
    on:click|stopPropagation
    on:focus|stopPropagation
    on:focusin|stopPropagation
    on:keydown|stopPropagation
    on:keyup|stopPropagation
    on:mousedown|preventDefault|stopPropagation
    on:mouseup|stopPropagation
    on:paste|stopPropagation
>
    <!-- TODO no focusin for now, as EditingArea will defer to Editable/Codable -->
    <textarea
        bind:this={textarea}
        value={initialValue}
        on:input={onInput}
        use:openCodemirror
    />
</div>

<style lang="scss">
    /* TODO there is global CSS in fields.scss */
    div :global(.mathjax-editor) {
        border-radius: 0;
        border-width: 0 1px;
        border-color: var(--medium-border);
    }
</style>
