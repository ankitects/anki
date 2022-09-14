<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    export let tooltip: string | undefined = undefined;
    export let active = false;
    export let disabled = false;
    export let tabbable = false;
</script>

<button
    class="label-button"
    class:active
    title={tooltip}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    button {
        padding: 0 calc(var(--buttons-size) / 3);
        font-size: var(--base-font-size);
        height: var(--buttons-size);

        border: none;
        appearance: none;
        background-color: white;

        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
</style>
