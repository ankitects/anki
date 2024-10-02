<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "$lib/sveltelib/theme";

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
    class="button-toolbar btn-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    style:--icon-align="baseline"
    {style}
    role="toolbar"
    on:focusout
>
    <slot />
</div>

<style lang="scss">
    .button-toolbar {
        flex-wrap: var(--buttons-wrap);
        padding-left: 0.15rem;

        :global(.button-group) {
            /* TODO replace with gap once available (blocked by Qt5 / Chromium 77) */
            margin-right: 0.3rem;
            margin-bottom: 0.15rem;
        }
    }
</style>
