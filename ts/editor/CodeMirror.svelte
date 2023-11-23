<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type CodeMirrorLib from "codemirror";
    import { EditorView } from "@codemirror/view";
    import type { Extension } from "@codemirror/state";

    export interface CodeMirrorAPI {
        readonly editor: Promise<CodeMirrorLib.Editor>;
        setOption<T extends keyof CodeMirrorLib.EditorConfiguration>(
            key: T,
            value: CodeMirrorLib.EditorConfiguration[T],
        ): Promise<void>;
    }
</script>

<script lang="ts">
    import { directionKey } from "@tslib/context-keys";
    import { promiseWithResolver } from "@tslib/promise";
    import { createEventDispatcher, getContext, onMount } from "svelte";
    import type { Writable } from "svelte/store";

    import { pageTheme } from "../sveltelib/theme";
    import {
        darkTheme,
        lightTheme,
        openCodeMirror,
        setupCodeMirror,
    } from "./code-mirror";

    export let configuration: Extension;
    export let code: Writable<string>;
    export let hidden = false;

    const defaultConfiguration = {
        rtlMoveVisually: true,
        lineNumbers: false,
    };

    const dispatch = createEventDispatcher();

    const updateListenerExtension = EditorView.updateListener.of((update) => {
        if (update.docChanged) {
            dispatch("change", update.state.doc.toString());
        }
    });

    function allConfiguration(): Extension {
        // {
        //         ...configuration,
        //         ...defaultConfiguration,
        //         direction: $direction,
        //         theme: $pageTheme.isDark ? darkTheme : lightTheme,
        //     },
        return [updateListenerExtension];
    }

    const [editorPromise, resolve] = promiseWithResolver<EditorView>();

    /**
     * Convenience function for editor.setOption.
     */
    async function setOption<T extends keyof CodeMirrorLib.EditorConfiguration>(
        key: T,
        value: CodeMirrorLib.EditorConfiguration[T],
    ): Promise<void> {
        const editor = await editorPromise;
        editor.setOption(key, value);
    }

    const direction = getContext<Writable<"ltr" | "rtl">>(directionKey);

    let apiPartial: Partial<CodeMirrorAPI>;
    export { apiPartial as api };

    Object.assign(apiPartial, {
        editor: editorPromise,
        setOption,
    });

    onMount(async () => {
        const editor = await editorPromise;
        setupCodeMirror(editor, code);
        // editor.on("focus", (codeMirror, event) =>
        //     dispatch("focus", { codeMirror, event }),
        // );
        // editor.on("blur", (codeMirror, event) =>
        //     dispatch("blur", { codeMirror, event }),
        // );
        // editor.on("keydown", (codeMirror, event) => {
        //     if (event.code === "Tab") {
        //         dispatch("tab", { codeMirror, event });
        //     }
        // });
    });
</script>

<div class="code-mirror">
    <textarea
        tabindex="-1"
        hidden
        use:openCodeMirror={{
            configuration: allConfiguration(),
            resolve,
            hidden,
        }}
    />
</div>

<style lang="scss">
    .code-mirror {
        height: 100%;

        :global(.CodeMirror) {
            height: auto;
        }

        :global(.CodeMirror-wrap pre) {
            word-break: break-word;
        }
    }
</style>
