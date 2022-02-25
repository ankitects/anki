<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type CodeMirrorLib from "codemirror";
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
                ...(baseOptions.extraKeys as CodeMirrorLib.KeyMap),
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

    let codeMirror = {} as CodeMirrorAPI;

    onMount(() =>
        codeMirror.editor.then((editor) => {
            focusAndCaretAfter(editor);

            if (selectAll) {
                editor.execCommand("selectAll");
            }

            let direction: "start" | "end" | undefined = undefined;

            editor.on(
                "keydown",
                (_instance: CodeMirrorLib.Editor, event: KeyboardEvent): void => {
                    if (event.key === "ArrowLeft") {
                        direction = "start";
                    } else if (event.key === "ArrowRight") {
                        direction = "end";
                    }
                },
            );

            editor.on(
                "beforeSelectionChange",
                (
                    instance: CodeMirrorLib.Editor,
                    obj: CodeMirrorLib.EditorSelectionChange,
                ): void => {
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
        }),
    );

    /**
     * Escape characters which are technically legal in Mathjax, but confuse HTML.
     */
    export function escapeSomeEntities(value: string): string {
        return value.replace(/</g, "{\\lt}").replace(/>/g, "{\\gt}");
    }
</script>

<div class="mathjax-editor">
    <CodeMirror
        {code}
        {configuration}
        bind:api={codeMirror}
        on:change={({ detail }) => code.set(escapeSomeEntities(detail))}
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
