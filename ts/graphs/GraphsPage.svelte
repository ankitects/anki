<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { SvelteComponentDev } from "svelte/internal";
    import { writable } from "svelte/store";

    import Page from "../components/Page.svelte";
    import { pageTheme } from "../sveltelib/theme";
    import WithGraphData from "./WithGraphData.svelte";

    export let initialSearch: string;
    export let initialDays: number;

    const search = writable(initialSearch);
    const days = writable(initialDays);

    export let graphs: typeof SvelteComponentDev[];
    export let controller: typeof SvelteComponentDev;

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
    <Page class="graphs-page">
        <svelte:component this={controller} slot="header" {search} {days} {loading} />
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
    </Page>
</WithGraphData>

<style lang="scss">
    .graphs-container {
        flex-grow: 1;
        display: grid;
        gap: 1em;
        grid-template-columns: repeat(3, minmax(0, 1fr));

        @media only screen and (max-width: 1400px) {
            grid-template-columns: 1fr 1fr;
        }
        @media only screen and (max-width: 1200px) {
            grid-template-columns: 1fr;
        }
    }
</style>
