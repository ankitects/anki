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
    let isCollapsed = false;
    let hidden = collapsed;

    const [outerPromise, outerResolve] = promiseWithResolver<HTMLElement>();
    const [innerPromise, innerResolve] = promiseWithResolver<HTMLElement>();

    let style: string;
    function setStyle(height: number, duration: number) {
        style = `--collapse-height: -${height}px; --duration: ${duration}ms`;
    }

    /* The following two functions use synchronous DOM-manipulation,
    because Editor field inputs would lose focus when using tick() */

    function getRequiredHeight(el: HTMLElement): number {
        el.style.setProperty("position", "absolute");
        el.style.setProperty("visibility", "hidden");
        el.removeAttribute("hidden");

        const height = el.clientHeight;

        el.setAttribute("hidden", "");
        el.style.removeProperty("position");
        el.style.removeProperty("visibility");

        return height;
    }

    async function transition(collapse: boolean) {
        const outer = await outerPromise;
        const inner = await innerPromise;

        outer.style.setProperty("overflow", "hidden");
        isCollapsed = true;

        const height = collapse ? inner.clientHeight : getRequiredHeight(inner);

        /* This function practically caps the maximum time at around 200ms,
           but still allows to differentiate between small and large contents */
        const duration = 10 + Math.pow(height, 1 / 4) * 20;

        setStyle(height, duration);

        if (!collapse) {
            inner.removeAttribute("hidden");
            isCollapsed = false;
        }

        const update = () => {
            inner.toggleAttribute("hidden", collapse);
            outer.style.removeProperty("overflow");
            hidden = collapse;
        };

        /* fallback required because transition only triggers if the height changes */
        if (height) {
            inner.addEventListener("transitionend", update, { once: true });
        } else {
            update();
        }
    }

    /* prevent transition on mount for performance reasons */
    let firstTransition = true;

    $: {
        transition(collapsed);
        firstTransition = false;
    }
</script>

<div {id} class="collapsible-container {className}" use:outerResolve>
    <div
        class="collapsible-inner"
        class:collapsed={isCollapsed}
        class:no-transition={firstTransition}
        use:innerResolve
        {style}
    >
        <slot {hidden} />
    </div>
</div>

<style lang="scss">
    .collapsible-container {
        position: relative;
    }
    .collapsible-inner {
        transition: margin-top var(--duration) ease-in;

        &.collapsed {
            margin-top: var(--collapse-height);
        }
        &.no-transition {
            transition: none;
        }
    }
</style>
