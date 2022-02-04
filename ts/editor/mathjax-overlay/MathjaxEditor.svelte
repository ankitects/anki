<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";
    import type { Writable } from "svelte/store";

    import * as tr from "../../lib/ftl";
    import { noop } from "../../lib/functional";
    import { getPlatformString } from "../../lib/shortcuts";
    import { baseOptions, focusAndCaretAfter, latex } from "../code-mirror";
    import type { CodeMirrorAPI } from "../CodeMirror.svelte";
    import CodeMirror from "../CodeMirror.svelte";

    export let code: Writable<string>;

    export let acceptShortcut: string;
    export let newlineShortcut: string;

    const configuration = {
        ...Object.assign({}, baseOptions, {
            extraKeys: {
                ...(baseOptions.extraKeys as CodeMirror.KeyMap),
                [acceptShortcut]: noop,
                [newlineShortcut]: noop,
            },
        }),
        placeholder: tr.editingMathjaxPlaceholder({
            accept: getPlatformString(acceptShortcut),
            newline: getPlatformString(newlineShortcut),
        }),
        mode: latex,
    };

    export let selectAll: boolean;

    const dispatch = createEventDispatcher();

    let codeMirror: CodeMirrorAPI = {} as CodeMirrorAPI;

    onMount(() => {
        focusAndCaretAfter(codeMirror.editor);

        if (selectAll) {
            codeMirror.editor.execCommand("selectAll");
        }

        let direction: "start" | "end" | undefined = undefined;

        codeMirror.editor.on("keydown", (_instance, event: KeyboardEvent) => {
            if (event.key === "ArrowLeft") {
                direction = "start";
            } else if (event.key === "ArrowRight") {
                direction = "end";
            }
        });

        codeMirror.editor.on(
            "beforeSelectionChange",
            (instance, obj: CodeMirror.EditorSelectionChange) => {
                const { anchor } = obj.ranges[0];

                if (anchor["hitSide"]) {
                    if (instance.getValue().length === 0) {
                        if (direction) {
                            dispatch(`moveout${direction}`);
                        }
                    } else if (anchor.line === 0 && anchor.ch === 0) {
                        dispatch("moveoutstart");
                    } else {
                        dispatch("moveoutend");
                    }
                }

                direction = undefined;
            },
        );
    });
</script>

<div class="mathjax-editor">
    <CodeMirror
        {code}
        {configuration}
        bind:api={codeMirror}
        on:change={({ detail }) => code.set(detail)}
        on:blur
    />
</div>

<style lang="scss">
    .mathjax-editor {
        :global(.CodeMirror) {
            max-width: 28rem;
            min-width: 14rem;
            margin-bottom: 0.25rem;
        }

        :global(.CodeMirror-placeholder) {
            font-family: sans-serif;
            font-size: 55%;
            text-align: center;
            color: var(--slightly-grey-text);
        }
    }
</style>
