<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronDown } from "./icons";
    import Popover from "./Popover.svelte";
    import WithFloating from "./WithFloating.svelte";

    export let id: string | undefined = undefined;
    let className = "";
    export { className as class };
    export let disabled = false;
    export let current: string = "";

    export let tooltip: string | undefined = undefined;

    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";
    export let element: HTMLElement | undefined = undefined;
    let hover = false;

    let showFloating = false;
    let clientWidth: number;
</script>

<WithFloating
    show={showFloating}
    offset={0}
    shift={0}
    hideArrow
    inline
    closeOnInsideClick
    on:close={() => (showFloating = false)}
    let:asReference
>
    <div
        {id}
        class="{className} select-container"
        class:rtl
        class:hover
        {disabled}
        title={tooltip}
        tabindex="-1"
        on:mouseenter={() => (hover = true)}
        on:mouseleave={() => (hover = false)}
        on:click={() => (showFloating = !showFloating)}
        bind:this={element}
        use:asReference
        bind:clientWidth
    >
        {current}
        <div class="chevron">
            <IconConstrain iconSize={80}>
                {@html chevronDown}
            </IconConstrain>
        </div>
    </div>
    <Popover slot="floating" scrollable --popover-width="{clientWidth}px">
        <slot />
    </Popover>
</WithFloating>

<style lang="scss">
    @use "sass/button-mixins" as button;
    .select-container {
        @include button.select($with-disabled: false);
        padding: 0.2rem 2rem 0.2rem 0.75rem;
        line-height: 1.5;
        height: var(--buttons-size, 100%);
        position: relative;
    }

    .chevron {
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        left: auto;
        border-left: 1px solid var(--border-subtle);
    }
    :global([dir="rtl"]) {
        .chevron {
            left: 0;
            right: auto;
            border-left: none;
            border-right: 1px solid var(--border-subtle);
        }
    }
</style>
