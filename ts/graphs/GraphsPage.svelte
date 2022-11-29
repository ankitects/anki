<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { SvelteComponentDev } from "svelte/internal";
    import { writable } from "svelte/store";

    import ScrollArea from "../components/ScrollArea.svelte";
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

<div class="graphs-page">
    <WithGraphData
        {search}
        {days}
        let:loading
        let:sourceData
        let:preferences
        let:revlogRange
    >
        {#if controller}
            <svelte:component
                this={controller}
                slot="header"
                {search}
                {days}
                {loading}
            />
        {/if}
        <ScrollArea scrollY>
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
        </ScrollArea>
    </WithGraphData>
</div>

<style lang="scss">
    .graphs-page {
        width: 100vw;
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
    .graphs-container {
        flex-grow: 1;
        display: grid;
        gap: 1.5em;
        grid-template-columns: 33.3% 33.3% 33.3%;
        padding-bottom: 1.5rem;

        @media only screen and (max-width: 1400px) {
            grid-template-columns: 50% 50%;
        }
        @media only screen and (max-width: 1200) {
            grid-template-columns: 100%;
        }
        @media only screen and (max-width: 600px) {
            font-size: 12px;
        }
    }
</style>
