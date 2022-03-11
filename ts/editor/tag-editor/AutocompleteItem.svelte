<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../../sveltelib/theme";

    export let selected = false;
    export let active = false;

    let buttonRef: HTMLButtonElement;

    $: if (selected && buttonRef) {
        /* buttonRef.scrollIntoView({ behavior: "smooth", block: "start" }); */
        /* TODO will not work on Gecko */
        (buttonRef as any).scrollIntoViewIfNeeded({
            behavior: "smooth",
            block: "start",
        });
    }
</script>

<button
    bind:this={buttonRef}
    tabindex="-1"
    class="autocomplete-item btn"
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
    class:selected
    class:active
    on:mousedown|preventDefault
    on:mouseup
    on:mouseenter
    on:mouseleave
>
    <slot />
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    .autocomplete-item {
        padding: 1px 7px 2px;

        text-align: start;
        white-space: nowrap;
        flex-grow: 1;
        border-radius: 0;
    }

    button {
        display: flex;
        justify-content: space-between;

        font-size: calc(var(--buttons-size) / 2.3);

        background: none;
        box-shadow: none !important;
        border: none;

        &.active {
            background-color: button.$focus-color !important;
            color: white !important;
        }
    }

    /* reset global CSS from buttons.scss */
    :global(.nightMode) button:hover {
        background-color: inherit;
    }

    /* extra specificity bc of global CSS reset above */
    button.btn-day {
        color: black;

        &.selected {
            background-color: #e9ecef;
            border-color: #e9ecef;
        }
    }

    button.btn-night {
        color: white;

        &.selected {
            @include button.btn-night-base;
        }
    }
</style>
