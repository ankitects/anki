<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    let buttonRef: HTMLButtonElement;

    export let tooltip: string | undefined = undefined;

    export let active = false;

    $: if (buttonRef && active) {
        /* buttonRef.scrollIntoView({ behavior: "smooth", block: "start" }); */
        /* TODO will not work on Gecko */
        (buttonRef as any).scrollIntoViewIfNeeded({
            behavior: "smooth",
            block: "start",
        });
    }

    export let tabbable = false;
</script>

<button
    bind:this={buttonRef}
    {id}
    tabindex={tabbable ? 0 : -1}
    class="dropdown-item {className}"
    class:active
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
    title={tooltip}
    on:mouseenter
    on:focus
    on:keydown
    on:click
    on:mousedown|preventDefault
>
    <slot />
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    button {
        display: flex;
        justify-content: start;

        font-size: var(--dropdown-font-size, var(--base-font-size));

        background: none;
        box-shadow: none !important;
        border: none;
        border-radius: 0;

        &:active,
        &.active {
            background-color: button.$focus-color;
            color: white;
        }
    }

    .btn-day {
        color: black;
    }

    .btn-night {
        color: white;

        &:hover,
        &:focus {
            @include button.btn-night-base;
        }
    }
</style>
