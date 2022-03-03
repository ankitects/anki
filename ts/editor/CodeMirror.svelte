<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type CodeMirrorLib from "codemirror";

    export interface CodeMirrorAPI {
        readonly editor: Promise<CodeMirrorLib.Editor>;
        setOption<T extends keyof CodeMirrorLib.EditorConfiguration>(
            key: T,
            value: CodeMirrorLib.EditorConfiguration[T],
        ): Promise<void>;
    }
</script>

<script lang="ts">
    import { createEventDispatcher, getContext, onMount } from "svelte";
    import type { Writable } from "svelte/store";

    import { directionKey } from "../lib/context-keys";
    import { promiseWithResolver } from "../lib/promise";
    import { pageTheme } from "../sveltelib/theme";
    import {
        darkTheme,
        lightTheme,
        openCodeMirror,
        setupCodeMirror,
    } from "./code-mirror";

    export let configuration: CodeMirrorLib.EditorConfiguration;
    export let code: Writable<string>;

    const defaultConfiguration = {
        rtlMoveVisually: true,
    };

    const [editorPromise, resolve] = promiseWithResolver<CodeMirrorLib.Editor>();

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

    $: setOption("direction", $direction);
    $: setOption("theme", $pageTheme.isDark ? darkTheme : lightTheme);

    let apiPartial: Partial<CodeMirrorAPI>;
    export { apiPartial as api };

    Object.assign(apiPartial, {
        editor: editorPromise,
        setOption,
    });

    const dispatch = createEventDispatcher();

    onMount(async () => {
        const editor = await editorPromise;
        setupCodeMirror(editor, code);
        editor.on("change", () => dispatch("change", editor.getValue()));
        editor.on("focus", () => dispatch("focus"));
        editor.on("blur", () => dispatch("blur"));
    });
</script>

<div class="code-mirror">
    <textarea
        tabindex="-1"
        hidden
        use:openCodeMirror={{
            configuration: { ...configuration, ...defaultConfiguration },
            resolve,
        }}
    />
</div>

<style lang="scss">
    .code-mirror :global(.CodeMirror) {
        height: auto;
    }
</style>
