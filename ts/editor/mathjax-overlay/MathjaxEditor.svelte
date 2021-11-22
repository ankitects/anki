<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import CodeMirror from "../CodeMirror.svelte";
    import type { Writable } from "svelte/store";
    import { baseOptions, latex } from "../code-mirror";
    import { getPlatformString } from "../../lib/shortcuts";
    import { noop } from "../../lib/functional";
    import * as tr from "../../lib/ftl";

    export let acceptShortcut: string;
    export let newlineShortcut: string;

    export let code: Writable<string>;

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
</script>

<div class="mathjax-editor">
    <CodeMirror
        {code}
        {configuration}
        on:change={({ detail }) => code.set(detail)}
        on:blur
        autofocus
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
