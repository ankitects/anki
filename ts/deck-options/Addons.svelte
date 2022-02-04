<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DeckOptionsState } from "./lib";
    import TitledContainer from "./TitledContainer.svelte";

    export let state: DeckOptionsState;

    const components = state.addonComponents;
    const auxData = state.currentAuxData;
</script>

{#if $components.length || state.haveAddons}
    <TitledContainer title="Add-ons">
        <p>
            If you're using an add-on that hasn't been updated to use this new screen
            yet, you can access the old deck options screen by holding down the shift
            key when opening the options.
        </p>

        {#each $components as addon}
            <svelte:component this={addon.component} bind:data={$auxData} {...addon} />
        {/each}
    </TitledContainer>
{/if}
