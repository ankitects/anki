<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { SvelteComponent } from "svelte/internal";
    import { writable } from "svelte/store";

    import { bridgeCommand } from "lib/bridgecommand";

    import WithGraphData from "./WithGraphData.svelte";

    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let initialSearch: string;
    export let initialDays: number;
    export let controller: SvelteComponent | null;

    const search = writable(initialSearch);
    const days = writable(initialDays);

    function browserSearch(event: CustomEvent) {
        bridgeCommand(`browserSearch: ${$search} ${event.detail.query}`);
    }
</script>

<div>
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

        {#if sourceData && preferences && revlogRange}
            {#each graphs as graph}
                <svelte:component
                    this={graph}
                    {sourceData}
                    {preferences}
                    {revlogRange}
                    {nightMode}
                    on:search={browserSearch}
                />
            {/each}
        {/if}
    </WithGraphData>
</div>

<style lang="scss">
    div {
        @media only screen and (max-width: 600px) {
            font-size: 12px;
        }
    }
</style>
