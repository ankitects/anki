<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import { pageTheme } from "../sveltelib/theme";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let tabbable: boolean = false;

    let buttonRef: HTMLButtonElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<button
    {id}
    tabindex={tabbable ? 0 : -1}
    bind:this={buttonRef}
    class="dropdown-item btn {className}"
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

        font-size: calc(var(--base-font-size) * 0.8);

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
