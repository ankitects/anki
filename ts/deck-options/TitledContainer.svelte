<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Section from "components/Section.svelte";

    export let title: string;
    export let api: Record<string, never> | undefined = undefined;
</script>

<div class="container-fluid mb-5">
    <h1>{title}</h1>

    <Section {api}>
        <slot />
    </Section>
</div>

<style lang="scss">
    h1 {
        border-bottom: 1px solid var(--medium-border);
    }

    .container-fluid {
        break-inside: avoid;
    }
</style>
