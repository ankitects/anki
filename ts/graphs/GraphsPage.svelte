<script lang="typescript">
    import "../sass/core.css";

    import type { SvelteComponent } from "svelte/internal";
    import type { I18n } from "anki/i18n";
    import type { PreferenceStore } from "./preferences";
    import type pb from "anki/backend_proto";
    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";
    import { bridgeCommand } from "anki/bridgecommand";

    import WithGraphData from "./WithGraphData.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let search: string;
    export let days: number;
    export let controller: SvelteComponent | null;

    const browserSearch = (search: string, query: string) => {
        bridgeCommand(`browserSearch:${search} ${query}`);
    };
</script>

<style lang="scss">
    @media only screen and (max-width: 600px) {
        .base {
            font-size: 12px;
        }
    }
</style>

<div class="base">
    <WithGraphData
        {search}
        {days}
        let:pending
        let:loading
        let:sourceData
        let:preferences
        let:revlogRange>
        {#if controller}
            <svelte:component this={controller} {i18n} {search} {days} {loading} />
        {/if}

        {#if !pending}
            {#each graphs as graph}
                <svelte:component
                    this={graph}
                    {sourceData}
                    {preferences}
                    {revlogRange}
                    {i18n}
                    {nightMode}
                    on:search={(event) => browserSearch(search, event.detail.query)} />
            {/each}
        {/if}
    </WithGraphData>
</div>
