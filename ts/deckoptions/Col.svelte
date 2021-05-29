<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    type Size = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12;
    type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl";

    export let breakpoint: Breakpoint | undefined = undefined;
    export let size: Size | undefined = undefined;
    export let grow = true;

    let colClass: string;
    $: {
        const breakpointComponent = breakpoint ? `-${breakpoint}` : "";
        const sizeComponent = size ? `-${size}` : "";
        colClass = "col" + breakpointComponent + sizeComponent;
    }
</script>

<div class={`${colClass} d-flex align-items-center`} class:flex-grow-0={!grow}>
    <slot />
</div>
