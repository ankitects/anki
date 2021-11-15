<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";

    export let title: string;
    export let api: Record<string, never> | undefined = undefined;
</script>

<div class="titled-container">
    <Container breakpoint="fluid" class="my-3" {api}>
        <h1>{title}</h1>

        <slot />
    </Container>
</div>

<style lang="scss">
    .titled-container {
        break-inside: avoid;
    }

    h1 {
        border-bottom: 1px solid var(--medium-border);
    }
</style>
