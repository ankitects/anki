<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Popover from "../components/Popover.svelte";
    import WithFloating from "../components/WithFloating.svelte";
    import IconConstrain from "./IconConstrain.svelte";
    import { chevronDown } from "./icons";

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
    placement="bottom"
    offset={0}
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
    @use "sass/input-mixins" as input;
    .select-container {
        @include input.select($with-disabled: false);
        padding: 0.2rem 2rem 0.2rem 0.75rem;

        position: relative;
    }

    .chevron {
        position: absolute;
        inset: 0 0 0 auto;
        border-left: 1px solid var(--button-border);
    }
    :global([dir="rtl"]) {
        .chevron {
            inset: 0 auto 0 0;
            border-left: none;
            border-right: 1px solid var(--button-border);
        }
    }
</style>
