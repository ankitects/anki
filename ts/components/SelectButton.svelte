<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import { pageTheme } from "../sveltelib/theme";

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };

    export let tooltip: string | undefined = undefined;
    export let disabled = false;

    let buttonRef: HTMLSelectElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { button: buttonRef }));
</script>

<!-- svelte-ignore a11y-no-onchange -->
<select
    tabindex="-1"
    bind:this={buttonRef}
    {id}
    {disabled}
    class="{className} form-select"
    class:btn-day={!$pageTheme.isDark}
    class:btn-night={$pageTheme.isDark}
    class:rtl
    title={tooltip}
    on:change
>
    <slot />
</select>
<div class="arrow" class:dark={$pageTheme.isDark} class:rtl />

<style lang="scss">
    @use "sass/button-mixins" as button;
    @include button.btn-day($with-hover: false);
    @include button.btn-night($with-hover: false);

    select {
        height: var(--buttons-size);
        /* Long option name can create overflow */
        text-overflow: ellipsis;
        /* Prevents text getting cropped on Windows */
        padding: {
            top: 0;
            bottom: 0;
        }
        &.rtl {
            direction: rtl;
            /* Reversed Bootstrap values */
            padding-left: 2.25rem;
            padding-right: 0.75rem;
        }
        &.btn-day {
            /* Hide default arrow for consistency */
            background: var(--canvas-elevated);
        }
    }

    .arrow {
        top: 0;
        right: 10px;
        &.rtl {
            left: 10px;
        }
        width: 15px;
        height: 100%;
        position: absolute;
        pointer-events: none;
        background: button.down-arrow(black) no-repeat right;
        &.dark {
            background-image: button.down-arrow(white);
        }
    }
</style>
