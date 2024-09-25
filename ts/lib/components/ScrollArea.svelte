<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    let className: string = "";
    export { className as class };
    export let scrollX = false;
    export let scrollY = false;
    let scrollBarHeight = 0;
    let measuring = true;

    const scrollStates = {
        top: false,
        right: false,
        bottom: false,
        left: false,
    };

    function measureScrollbar(el: HTMLDivElement) {
        scrollBarHeight = el.offsetHeight - el.clientHeight;
        measuring = false;
    }

    const callback = (entries: IntersectionObserverEntry[]) => {
        entries.forEach((entry) => {
            scrollStates[entry.target.getAttribute("data-edge")!] =
                !entry.isIntersecting;
        });
    };

    let observer: IntersectionObserver;
    function initObserver(el: HTMLDivElement) {
        observer = new IntersectionObserver(callback, { root: el });
        for (const edge of el.getElementsByClassName("scroll-edge")) {
            observer.observe(edge);
        }
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
            use:measureScrollbar
            use:initObserver
        >
            <div class="d-flex flex-column flex-grow-1">
                <div class="scroll-edge" data-edge="top"></div>
                <div class="d-flex flex-row flex-grow-1">
                    <div class="scroll-edge" data-edge="left"></div>
                    <div class="scroll-content flex-grow-1">
                        <slot />
                    </div>
                    <div class="scroll-edge" data-edge="right"></div>
                </div>
                <div class="scroll-edge" data-edge="bottom"></div>
            </div>
        </div>

        {#if scrollStates.top}
            <div class="scroll-shadow top-0"></div>
        {/if}
        {#if scrollStates.bottom}
            <div class="scroll-shadow bottom-0"></div>
        {/if}
        {#if scrollStates.left}
            <div class="scroll-shadow start-0"></div>
        {/if}
        {#if scrollStates.right}
            <div class="scroll-shadow end-0"></div>
        {/if}
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
