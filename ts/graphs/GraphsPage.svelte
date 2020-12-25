<script context="module">
    // import _ from "./graph-helpers.scss";
</script>

<script lang="typescript">
    import RangeBox from './RangeBox.svelte'
    import { afterUpdate } from 'svelte';

    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";
    import { getGraphData, RevlogRange } from "./graph-helpers";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: any[];

    export let search: string;
    export let days: number;
    export let withRangeBox: boolean;

    let dataPromise: Promise<pb.backend.GraphsOut>;
    let revlogRange: RevlogRange;

    const refreshWith = (search, days, revlogRange) => {
        dataPromise = getGraphData(search, days);
        revlogRange = days > 365
                ? RevlogRange.All
                : RevlogRange.Year;
    }

    const refresh = (event) => {
        refreshWith(event.detail.search, event.detail.days)
    }

    refreshWith(search, days)

    let spinner: HTMLDivElement;
    let graphsContainer: HTMLDivElement;

    afterUpdate(() => {
        // make sure graph container retains its size for spinner
        if (spinner) {
            graphsContainer.style.minHeight = `${document.documentElement.scrollHeight}px`;
        }
        else {
            graphsContainer.style.minHeight = '';
        }
    })
</script>

{#if withRangeBox}
    <RangeBox {i18n} {search} {days} on:update={refresh} />
{/if}

<div bind:this={graphsContainer} tabindex="-1" class="no-focus-outline">
    {#await dataPromise}
        <div bind:this={spinner} class="spin">◐</div>
    {:then sourceData}
            {#each graphs as Graph}
                <Graph {sourceData} {revlogRange} {i18n} {nightMode} />
            {/each}
    {:catch error}
        <script>
            alert({i18n.tr(i18n.TR.STATISTICS_ERROR_FETCHING)});
        </script>
    {/await}
</div>
