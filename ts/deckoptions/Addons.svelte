<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TitledContainer from "./TitledContainer.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    let components = state.addonComponents;
    const auxData = state.currentAuxData;
</script>

{#if $components.length || state.haveAddons}
    <TitledContainer title="Add-ons" {api}>
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
