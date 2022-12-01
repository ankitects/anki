<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, tick } from "svelte";

    import { promiseWithResolver } from "../lib/promise";

    let className: string = "";
    export { className as class };

    let element: HTMLElement;

    export let scrollX = false;
    export let scrollY = false;

    let clientWidth = 0;
    let clientHeight = 0;
    let scrollWidth = 0;
    let scrollHeight = 0;
    let scrollTop = 0;
    let scrollLeft = 0;

    $: overflowTop = scrollTop > 0;
    $: overflowBottom = scrollTop < scrollHeight - clientHeight;
    $: overflowLeft = scrollLeft > 0;
    $: overflowRight = scrollLeft < scrollWidth - clientWidth;

    async function updateScrollState(): Promise<void> {
        scrollHeight = element.scrollHeight;
        scrollWidth = element.scrollWidth;
        scrollTop = element.scrollTop;
        scrollLeft = element.scrollLeft;
    }

    let scrollBarWidth = 0;
    let scrollBarHeight = 0;
    let measuring = true;

    onMount(async function measureScrollbar() {
        scrollBarWidth = element.offsetWidth - element.clientWidth;
        scrollBarHeight = element.offsetHeight - element.clientHeight;

        measuring = false;
    });
</script>

<div class="scroll-area-relative">
    <div class="scroll-area {className}">
        <div
            class="scroll-area-content"
            class:measuring
            class:scroll-x={scrollX}
            class:scroll-y={scrollY}
            style:--scrollbar-height="{scrollBarHeight}px"
            bind:this={element}
            bind:clientWidth
            bind:clientHeight
            on:scroll={updateScrollState}
            on:resize={updateScrollState}
        >
            <slot />
        </div>

        {#if overflowTop} <div class="scroll-shadow top" /> {/if}
        {#if overflowBottom} <div class="scroll-shadow bottom" /> {/if}
        {#if overflowLeft} <div class="scroll-shadow left" /> {/if}
        {#if overflowRight} <div class="scroll-shadow right" /> {/if}
    </div>
</div>

<style lang="scss">
    $shadow-top: inset 0 5px 5px -5px var(--shadow);
    $shadow-bottom: inset 0 -5px 5px -5px var(--shadow);
    $shadow-left: inset 5px 0 5px -5px var(--shadow);
    $shadow-right: inset -5px 0 5px -5px var(--shadow);

    .scroll-area-relative {
        height: calc(var(--height) + var(--scrollbar-height));
        flex-grow: 1;
        position: relative;
    }

    .scroll-area-content {
        position: absolute;
        height: 100%;
        width: 100%;
        display: flex;
        flex-direction: column;

        overflow: auto;
        &.scroll-x {
            overflow-x: scroll;
            overflow-y: hidden;
        }
        &.scroll-y {
            overflow-y: scroll;
            overflow-x: hidden;
        }

        &.measuring {
            visibility: hidden;
            overflow: scroll;
        }
    }

    .scroll-shadow {
        position: absolute;
        pointer-events: none;
        // z-index between LabelContainer (editor) and FloatingArrow
        z-index: 55;

        &.top,
        &.bottom {
            left: 0;
            right: 0;
            height: 5px;
        }
        &.top {
            top: 0;
            box-shadow: $shadow-top;
        }
        &.bottom {
            bottom: 0;
            box-shadow: $shadow-bottom;
        }
        &.left,
        &.right {
            top: 0;
            bottom: 0;
            width: 5px;
        }

        &.left {
            left: 0;
            box-shadow: $shadow-left;
        }
        &.right {
            right: 0;
            box-shadow: $shadow-right;
        }
    }
</style>
