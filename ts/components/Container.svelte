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

    export let breakpoint: Breakpoint | "fluid" | undefined = undefined;
    export let api: Record<string, never> | undefined = undefined;

    $: containerClass = breakpoint ? `container-${breakpoint}` : "container";
</script>

<div {id} class="{containerClass} {className}">
    <Section {api}>
        <slot />
    </Section>
</div>
