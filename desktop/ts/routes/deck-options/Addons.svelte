<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TitledContainer from "$lib/components/TitledContainer.svelte";

    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;

    const components = state.addonComponents;
    const auxData = state.currentAuxData;
</script>

{#if $components.length}
    <TitledContainer title="Add-ons">
        {#each $components as addon}
            <svelte:component this={addon.component} bind:data={$auxData} {...addon} />
        {/each}
    </TitledContainer>
{/if}
