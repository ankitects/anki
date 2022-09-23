<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";
    import IconConstrain from "./IconConstrain.svelte";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let active = false;
    export let disabled = false;
    export let tabbable = false;

    export let iconSize = 75;
    export let widthMultiplier = 1;
    export let flipX = false;
</script>

<button
    {id}
    class="icon-button btn {className}"
    class:active
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
    title={tooltip}
    {disabled}
    tabindex={tabbable ? 0 : -1}
    on:click
    on:mousedown|preventDefault
>
    <IconConstrain {flipX} {widthMultiplier} {iconSize}>
        <slot />
    </IconConstrain>
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    .icon-button {
        @include button.base;
        @include button.border-radius;

        padding: 0;
        font-size: var(--base-font-size);
        height: var(--buttons-size);
        min-width: calc(var(--buttons-size) * 0.75);
    }

</style>
