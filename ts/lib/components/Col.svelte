<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Breakpoint } from "./types";

    let className: string = "";
    export { className as class };

    /* flex-basis: 100% if viewport < breakpoint otherwise
     * as specified by --cols and --col-size */
    export let breakpoint: Breakpoint = "xs";
</script>

<div
    class="col {className}"
    class:col-xs={breakpoint === "xs"}
    class:col-sm={breakpoint === "sm"}
    class:col-md={breakpoint === "md"}
    class:col-lg={breakpoint === "lg"}
    class:col-xl={breakpoint === "xl"}
    class:col-xxl={breakpoint === "xxl"}
>
    <slot />
</div>

<style lang="scss">
    @use "../sass/breakpoints" as bp;

    .col {
        display: flex;
        flex-flow: row nowrap;
        align-items: var(--col-align, flex-start);
        justify-content: var(--col-justify, flex-start);
        padding: 0 var(--gutter-inline, 0);
        flex: 1 0 100%;
    }

    $calc: calc(100% / var(--cols, 1) * var(--col-size, 1));

    @include bp.with-breakpoints(
        "col",
        (
            "flex-basis": (
                "xs": $calc,
                "sm": $calc,
                "md": $calc,
                "lg": $calc,
                "xl": $calc,
                "xxl": $calc,
            ),
        )
    );
</style>
