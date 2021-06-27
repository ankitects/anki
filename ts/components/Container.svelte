<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Section from "./Section.svelte";

    export let id: string | undefined = undefined;
    let className: string = "";
    export { className as class };

    export let api: Record<string, never> | undefined = undefined;

    export let rows: number | undefined = undefined;
    $: style = rows ? `--rows: ${rows}` : undefined;
</script>

<div {id} class={`container mb-1 ${className}`} {style}>
    <Section {api}>
        <slot />
    </Section>
</div>
