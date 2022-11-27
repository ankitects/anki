<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { promiseWithResolver } from "@tslib/promise";

    export let scroll = true;
    const [element, elementResolve] = promiseWithResolver<HTMLElement>();

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

    $: shadows = {
        top: overflowTop ? "0 5px" : null,
        bottom: overflowBottom ? "0 -5px" : null,
        left: overflowLeft ? "5px 0" : null,
        right: overflowRight ? "-5px 0" : null,
    };
    const rest = "5px -5px var(--shadow)";

    $: shadow = Array.from(
        Object.values(shadows).filter((v) => v != null),
        (v) => `inset ${v} ${rest}`,
    ).join(", ");

    async function updateScrollState(): Promise<void> {
        const el = await element;
        scrollHeight = el.scrollHeight;
        scrollWidth = el.scrollWidth;
        scrollTop = el.scrollTop;
        scrollLeft = el.scrollLeft;
    }
</script>

<div
    class="pane-content"
    class:scroll
    style:--box-shadow={shadow}
    style:--client-height="{clientHeight}px"
    use:elementResolve
    bind:clientHeight
    bind:clientWidth
    on:scroll={updateScrollState}
    on:resize={updateScrollState}
>
    <slot />
</div>

<style lang="scss">
    .pane-content {
        display: flex;
        flex-direction: column;
        flex-grow: 1;
        overflow: hidden;
        &.scroll {
            overflow: auto;
        }
        /* force box-shadow to be rendered above children */
        &::before {
            content: "";
            position: fixed;
            pointer-events: none;
            left: 0;
            right: 0;
            z-index: 4;
            height: var(--client-height);
            box-shadow: var(--box-shadow);
            transition: box-shadow 0.1s ease-in-out;
        }
    }
</style>
