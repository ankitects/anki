<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import { defaultGraphBounds } from "./graphs";
    import {
        gatherData,
        renderFutureDue,
        GraphData,
        FutureDueRange,
        buildHistogram,
    } from "./future-due";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    let graphData = null as GraphData | null;
    let histogramData = null as HistogramData | null;

    let svg = null as HTMLElement | SVGElement | null;
    let range = FutureDueRange.Month;

    $: if (sourceData) {
        console.log("gathering data");
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        console.log("preparing data");
        histogramData = buildHistogram(graphData, range);
    }
</script>

{#if histogramData}

    <div class="graph">
        <h1>Future Due</h1>

        <div class="range-box-inner">
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Month} />
                Month
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Quarter} />
                3 months
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Year} />
                Year
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.AllTime} />
                All time
            </label>
        </div>

        <HistogramGraph
            data={histogramData}
            xText="Days from now"
            yText="Number of cards" />

    </div>
{/if}
