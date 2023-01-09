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
        gap: 1em;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        // required on Safari to stretch whole width
        width: calc(100vw - 3em);
        margin-left: 1em;
        margin-right: 1em;

        @media only screen and (max-width: 600px) {
            width: calc(100vw - 1rem);
            margin-left: 0.5rem;
            margin-right: 0.5rem;
        }

        @media only screen and (max-width: 1400px) {
            grid-template-columns: 1fr 1fr;
        }
        @media only screen and (max-width: 1200px) {
            grid-template-columns: 1fr;
        }
        @media only screen and (max-width: 600px) {
            font-size: 12px;
        }

        @media only print {
            // grid layout does not honor page-break-inside
            display: block;
            margin-top: 3em;
        }
    }

    .spacer {
        height: 1.5em;
    }
</style>
