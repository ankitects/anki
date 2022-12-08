<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import { promiseWithResolver } from "../lib/promise";

    let className: string = "";
    export { className as class };

    const [element, elementResolve] = promiseWithResolver<HTMLDivElement>();

    export let scrollX = false;
    export let scrollY = false;

    let clientWidth = 0;
    let clientHeight = 0;

    let scrollBarWidth = 0;
    let scrollBarHeight = 0;
    let measuring = true;

    onMount(async function measureScrollbar() {
        const el = await element;
        scrollBarWidth = el.offsetWidth - el.clientWidth;
        scrollBarHeight = el.offsetHeight - el.clientHeight;

        measuring = false;
    });

    const scrollStates = {
        top: false,
        right: false,
        bottom: false,
        left: false,
    };

    const callback = (entries: IntersectionObserverEntry[]) => {
        entries.forEach((entry) => {
            scrollStates[entry.target.getAttribute("data-edge")!] =
                !entry.isIntersecting;
        });
    };

    let observer: IntersectionObserver;

    async function initObserver() {
        observer = new IntersectionObserver(callback, { root: await element });
    }

    function observe(edge: HTMLDivElement) {
        (async (edge) => {
            if (!observer) {
                await initObserver();
            }
            observer.observe(edge);
        })(edge);
    }
</script>

<div class="scroll-area-relative">
    <div class="scroll-area-wrapper {className}">
        <div
            class="scroll-area"
            class:measuring
            class:scroll-x={scrollX}
            class:scroll-y={scrollY}
            style:--scrollbar-height="{scrollBarHeight}px"
            bind:clientWidth
            bind:clientHeight
            use:elementResolve
        >
            <div class="d-flex flex-column flex-grow-1">
                <div class="scroll-edge" data-edge="top" use:observe />
                <div class="d-flex flex-row flex-grow-1">
                    <div class="scroll-edge" data-edge="left" use:observe />
                    <div class="scroll-content flex-grow-1">
                        <slot />
                    </div>
                    <div class="scroll-edge" data-edge="right" use:observe />
                </div>
                <div class="scroll-edge" data-edge="bottom" use:observe />
            </div>
        </div>

        {#if scrollStates.top} <div class="scroll-shadow top-0" /> {/if}
        {#if scrollStates.bottom} <div class="scroll-shadow bottom-0" /> {/if}
        {#if scrollStates.left} <div class="scroll-shadow start-0" /> {/if}
        {#if scrollStates.right} <div class="scroll-shadow end-0" /> {/if}
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

    .scroll-area {
        position: absolute;
        height: 100%;
        width: 100%;
        display: flex;
        flex-direction: column;
        overscroll-behavior: none;

        overflow: auto;
        &.scroll-x {
            overflow-x: auto;
            overflow-y: hidden;
            overscroll-behavior-y: auto;
        }
        &.scroll-y {
            overflow-y: auto;
            overflow-x: hidden;
            overscroll-behavior-x: none;
        }

        &.measuring {
            visibility: hidden;
            overflow: scroll;
        }
    }

    .scroll-edge {
        &[data-edge="top"],
        &[data-edge="bottom"] {
            height: 1px;
        }
        &[data-edge="left"],
        &[data-edge="right"] {
            width: 1px;
        }
    }

    .scroll-shadow {
        position: absolute;
        pointer-events: none;
        // z-index between LabelContainer (editor) and FloatingArrow
        z-index: 55;

        &.top-0,
        &.bottom-0 {
            left: 0;
            right: 0;
            height: 5px;
        }
        &.start-0,
        &.end-0 {
            top: 0;
            bottom: 0;
            width: 5px;
        }
        &.top-0 {
            box-shadow: $shadow-top;
        }
        &.bottom-0 {
            box-shadow: $shadow-bottom;
        }
        &.start-0 {
            box-shadow: $shadow-left;
        }
        &.end-0 {
            box-shadow: $shadow-right;
        }
    }
</style>
