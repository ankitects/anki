<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let size = -1;
    export let wrap = false;

    let buttonWrap: string;
    $: buttonWrap = wrap ? `wrap` : `nowrap`;
</script>

<div
    {id}
    class="button-toolbar btn-toolbar {className}"
    class:nightMode={$pageTheme.isDark}
    style:--button-size="{size}rem"
    style:--button-wrap="{buttonWrap}rem"
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
            /* TODO replace with gap once available */
            margin-right: 0.15rem;
            margin-bottom: 0.15rem;
        }
    }
</style>
