<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import { pageTheme } from "../../sveltelib/theme";

    let className: string = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let selected = false;

    const dispatch = createEventDispatcher();

    let flashing: boolean = false;

    export function flash(): void {
        flashing = true;
        setTimeout(() => (flashing = false), 300);
    }

    let button: HTMLButtonElement;

    onMount(() => dispatch("mount", { button }));
</script>

<button
    bind:this={button}
    class="tag btn d-inline-flex align-items-center text-nowrap ps-2 pe-1 {className}"
    class:selected
    class:flashing
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
    tabindex="-1"
    title={tooltip}
    on:mousemove
    on:click
>
    <slot />
</button>

<style lang="scss">
    @use "sass/button-mixins" as button;

    @keyframes flash {
        0% {
            filter: invert(0);
        }
        50% {
            filter: invert(0.4);
        }
        100% {
            filter: invert(0);
        }
    }

    .tag {
        font-size: var(--base-font-size);
        padding: 0;

        --border-color: var(--medium-border);

        border: 1px solid var(--border-color) !important;
        border-radius: 5px;

        &:focus,
        &:active {
            outline: none;
            box-shadow: none;
        }

        &.flashing {
            animation: flash 0.3s linear;
        }

        &.selected {
            box-shadow: 0 0 0 2px var(--focus-shadow);
            --border-color: var(--focus-border);
        }
    }

    @include button.btn-day($with-active: false, $with-disabled: false);

    @include button.btn-night($with-active: false, $with-disabled: false);
</style>
