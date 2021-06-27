<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Section from "components/Section.svelte";
    import { onMount } from "svelte";
    import { lineCount } from "./DeckOptionsPage.svelte";

    export let title: string;
    export let api: Record<string, never> | undefined = undefined;

    let container: HTMLElement;
    let style: string | undefined;
    let span: number;

    onMount(() => {
        span = container.querySelectorAll(".row").length | 1;
        lineCount.update((n) => n + span);
        style = `--size: ${span}`;
    });
</script>

<div class="container-fluid my-4" {style} bind:this={container}>
    <h1>{title}</h1>

    <Section {api}>
        <slot />
    </Section>
</div>

<style lang="scss">
    h1 {
        border-bottom: 1px solid var(--medium-border);
        font-size: clamp(1rem, calc(1.375rem + 1.5vw), 2rem);
    }
    div {
        grid-row: span var(--size);
    }
</style>
