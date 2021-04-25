<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    // import * as tr from "lib/i18n";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let components = state.addonComponents;
    const auxData = state.currentAuxData;
</script>

{#if $components.length}
    <div>
        <h2>Add-ons</h2>

        {#each $components as addon}
            <svelte:component this={addon.component} bind:data={$auxData} {...addon} />
        {/each}
    </div>
{/if}
