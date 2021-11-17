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
    export let api: Record<string, never> | undefined = undefined;
</script>

<div {id} bind:offsetHeight={height} class="sticky-container {className}">
    <Container {breakpoint} {api}>
        <slot />
    </Container>
</div>

<style lang="scss">
    .sticky-container {
        position: sticky;
        top: 0;
        bottom: 0;
        left: 0;
        right: 0;
        z-index: 10;

        background: var(--window-bg);
        border-style: solid;
        border-color: var(--medium-border);
        border-width: var(--sticky-borders, 0);
    }
</style>
