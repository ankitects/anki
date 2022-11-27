<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { SvelteComponentDev } from "svelte/internal";
    import { writable } from "svelte/store";
    
    import { pageTheme } from "../sveltelib/theme";
    import WithGraphData from "./WithGraphData.svelte";

    export let initialSearch: string;
    export let initialDays: number;

    const search = writable(initialSearch);
    const days = writable(initialDays);

    export let graphs: typeof SvelteComponentDev[];
    export let controller: typeof SvelteComponentDev | null;

    function browserSearch(event: CustomEvent) {
        bridgeCommand(`browserSearch: ${$search} ${event.detail.query}`);
    }
</script>

<WithGraphData
    {search}
    {days}
    let:loading
    let:sourceData
    let:preferences
    let:revlogRange
>
    {#if controller}
        <svelte:component this={controller} {search} {days} {loading} />
    {/if}

    <div class="graphs-container">
        {#if sourceData && preferences && revlogRange}
            {#each graphs as graph}
                <svelte:component
                    this={graph}
                    {sourceData}
                    {preferences}
                    {revlogRange}
                    nightMode={$pageTheme.isDark}
                    on:search={browserSearch}
                />
            {/each}
        {/if}
    </div>
    <div class="spacer" />
</WithGraphData>

<style lang="scss">
    .graphs-container {
        display: grid;
        gap: 1.5em;
        grid-template-columns: 50% 50%;

        @media only screen and (max-width: 1200px) {
            grid-template-columns: 100%;
        }
        @media only screen and (max-width: 600px) {
            font-size: 12px;
        }
    }

    .spacer {
        height: 1.5em;
    }
</style>
