<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "./Container.svelte";
    import type { Breakpoint } from "./types";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let height: number = 0;
    export let breakpoint: Breakpoint | "fluid" = "fluid";
</script>

<div {id} bind:offsetHeight={height} class="sticky-container {className}">
    <Container {breakpoint}>
        <slot />
    </Container>
</div>

<style lang="scss">
    .sticky-container {
        position: sticky;
        top: var(--sticky-top, 0);
        bottom: 0;
        left: 0;
        right: 0;
        z-index: var(--z-index, 50);

        background: var(--sticky-bg, var(--canvas));
        border-style: solid;
        border-color: var(--sticky-border, var(--border));
        border-width: var(--sticky-borders, 0);
    }
</style>
