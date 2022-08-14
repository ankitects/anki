<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { promiseWithResolver } from "../lib/promise";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let collapsed = false;
    let height = 0;

    const [element, elementResolve] = promiseWithResolver<HTMLElement>();
    let isCollapsed = false;

    let style: string;
    function setStyle(height: number, duration: number) {
        style = `--collapse-height: -${height}px; --duration: ${duration}ms`;
    }

    let transitioning = false;

    async function transition(collapse: boolean) {
        const inner = await element;
        transitioning = true;
        isCollapsed = true;

        const height = inner.clientHeight;
        const duration = Math.sqrt(height * 80);

        if (collapse) {
            setStyle(height, duration);
        } else {
            inner.removeAttribute("hidden");
            isCollapsed = false;
        }

        inner.addEventListener(
            "transitionend",
            () => {
                if (collapse) inner.setAttribute("hidden", "");
                transitioning = false;
            },
            { once: true },
        );

        // fallback for initially collapsed items where transition isn't possible
        setTimeout(() => {
            transitioning = false;
        }, duration);
    }

    $: transition(collapsed);
</script>

<div {id} class="collapsible-container {className}" class:transitioning>
    <div
        class="collapsible-inner"
        class:collapsed={isCollapsed}
        use:elementResolve
        {style}
    >
        <slot />
    </div>
</div>

<style lang="scss">
    .collapsible-container {
        position: relative;
        &.transitioning {
            overflow: hidden;
        }
    }
    .collapsible-inner {
        transition: margin-top var(--duration) ease-in;

        &.collapsed {
            margin-top: var(--collapse-height);
        }
    }
</style>
