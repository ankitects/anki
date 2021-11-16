<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Section from "./Section.svelte";
    import type { Breakpoint } from "./types";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    /* width: 100% if <= breakpoint otherwise with gutters */
    export let breakpoint: Breakpoint | "fluid" = "fluid";
    export let api: Record<string, never> | undefined = undefined;
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
    <Section {api}>
        <slot />
    </Section>
</div>

<style lang="scss">
    @use "sass/breakpoints";

    .container {
        display: flex;
        flex-direction: var(--container-direction, column);

        padding: var(--gutter-block, 0) var(--gutter-inline, 0);
        margin: var(--container-margin, 0) var(--container-gutter, auto);
    }

    .container-fluid {
        width: 100%;
        height: 100%;

        --container-gutter: 0;
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
                "xxl": 1320px
            )
        )
    );
</style>
