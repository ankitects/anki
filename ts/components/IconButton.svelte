<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let tooltip: string | undefined = undefined;
    export let active = false;

    /**
     * Makes the button unclickable, but will not apply adequate styling.
     * To achieve styling, pass disabled to the ButtonGroup as well.
     */
    export let disabled = false;
    export let tabbable = false;
</script>

<button
    class="button"
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
    @use "sass/button-mixins" as button;

    .button {
        position: relative;
        padding: 3px var(--padding-inline, 3px);

        font-size: var(--base-font-size);
        height: var(--buttons-size);

        @include button.base;
        @include button.pressed;
        @include button.hover;
        @include button.active(".active");
        @include button.disabled;

        > :global(svg) {
            width: 100%;
            height: 100%
        }
    }
</style>
