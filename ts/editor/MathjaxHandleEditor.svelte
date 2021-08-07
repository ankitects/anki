<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher } from "svelte";
    import { ChangeTimer } from "./change-timer";

    import CodeMirror from "codemirror";
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

    const noop = () => {
        /* noop */
    };

    const codeMirrorOptions = {
        mode: latex,
        theme: "monokai",
        lineWrapping: true,
        matchTags: { bothTags: true },
        extraKeys: { Tab: noop, "Shift-Tab": noop },
        viewportMargin: Infinity,
        lineWiseCopyCut: false,
        autofocus: true,
    };

    let codeMirror: CodeMirror.EditorFromTextArea;
    const changeTimer = new ChangeTimer();

    function onInput() {
        changeTimer.schedule(
            () => dispatch("update", { mathjax: codeMirror.getValue() }),
            400
        );
    }

    function openCodemirror(textarea: HTMLTextAreaElement): void {
        codeMirror = CodeMirror.fromTextArea(textarea, codeMirrorOptions);
        codeMirror.on("change", onInput);
    }

    const dispatch = createEventDispatcher();
    let textarea: HTMLTextAreaElement;

    onMount(() => {
        codeMirror.focus();
        codeMirror.setCursor(codeMirror.lineCount(), 0);
    });
</script>

<!-- <textarea bind:value use:openCodemirror /> -->
<div
    on:click|stopPropagation
    on:focus|stopPropagation
    on:keydown|stopPropagation
    on:focusin|stopPropagation
    on:keyup|stopPropagation
    on:mouseup|stopPropagation
>
    <!-- TODO no focusin for now, as EditingArea will defer to Editable/Codable -->
    <textarea
        bind:this={textarea}
        value={initialValue}
        on:mouseup|preventDefault|stopPropagation
        on:keyup|stopPropagation
        on:click|stopPropagation
        on:focusin|stopPropagation
        on:input={onInput}
        use:openCodemirror
    />
</div>
