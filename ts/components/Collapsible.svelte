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
    function setStyle(el: HTMLElement) {
        height = el.clientHeight;
        style = `--collapse-height: -${height}px; --duration: ${Math.sqrt(
            height * 80,
        )}ms`;
    }

    async function transition(collapse: boolean) {
        const inner = await element;
        isCollapsed = true;

        if (collapse) {
            setStyle(inner);

            inner.addEventListener(
                "transitionend",
                () => {
                    // DOM-manipulation is required here
                    inner.setAttribute("hidden", "");
                },
                { once: true },
            );
        } else {
            inner.removeAttribute("hidden");
            isCollapsed = false;
        }
    }

    $: transition(collapsed);
</script>

<div {id} class="collapsible-container {className}">
    <div
        class="collapsible-inner"
        class:is-collapsed={isCollapsed}
        use:elementResolve
        {style}
    >
        <slot />
    </div>
</div>

<style lang="scss">
    .collapsible-container {
        position: relative;
        overflow: hidden;
    }
    .collapsible-inner {
        transition: margin-top var(--duration) ease-in;

        &.is-collapsed {
            margin-top: var(--collapse-height);
        }
    }
</style>
