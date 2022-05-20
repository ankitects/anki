<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let size: number | undefined = undefined;
    export let wrap: boolean | undefined = undefined;

    $: buttonSize = size ? `--buttons-size: ${size}rem; ` : "";
    let buttonWrap: string;
    $: if (wrap === undefined) {
        buttonWrap = "";
    } else {
        buttonWrap = wrap ? `--buttons-wrap: wrap; ` : `--buttons-wrap: nowrap; `;
    }

    $: style = buttonSize + buttonWrap;
</script>

<div
    {id}
    class="button-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    {style}
    role="toolbar"
    on:focusout
>
    <slot />
</div>

<style lang="scss">
    .button-toolbar {
        display: flex;
        flex-wrap: var(--buttons-wrap);

        justify-content: flex-start;

        :global(.button-group) {
            /* TODO replace with gap once available */
            margin-right: 4px;
            margin-bottom: 2px;
        }
    }
</style>
