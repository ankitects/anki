<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Breakpoint } from "./types";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    /* width: 100% if viewport < breakpoint otherwise with gutters */
    export let breakpoint: Breakpoint | "fluid" = "fluid";
</script>

<div
    {id}
    class="container {className}"
    class:container-xs={breakpoint === "xs"}
    class:container-sm={breakpoint === "sm"}
    class:container-md={breakpoint === "md"}
    class:container-lg={breakpoint === "lg"}
    class:container-xl={breakpoint === "xl"}
    class:container-xxl={breakpoint === "xxl"}
    class:container-fluid={breakpoint === "fluid"}
>
    <slot />
</div>

<style lang="scss">
    @use "../sass/breakpoints";

    .container {
        display: flex;
        flex-direction: var(--container-direction, column);

        padding: var(--gutter-block, 0) var(--gutter-inline, 0);
        margin: 0 auto;

        &.container-fluid {
            width: 100%;
            height: 100%;

            margin: 0;
        }
    }

    @include breakpoints.with-breakpoints-upto(
        "container",
        (
            "max-width": (
                "xs": 360px,
                "sm": 540px,
                "md": 720px,
                "lg": 960px,
                "xl": 1140px,
                "xxl": 1320px,
            ),
        )
    );
</style>
