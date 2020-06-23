<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import {
        gatherIntervalData,
        IntervalRange,
        prepareIntervalData,
        IntervalGraphData,
    } from "./intervals";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let data: pb.BackendProto.GraphsOut | null = null;

    let svg = null as HTMLElement | SVGElement | null;
    let range = IntervalRange.Percentile95;
    let histogramData = null as HistogramData | null;

    let intervalData: IntervalGraphData | null = null;
    $: if (data) {
        console.log("gathering data");
        intervalData = gatherIntervalData(data);
    }

    $: if (intervalData) {
        console.log("preparing data");
        histogramData = prepareIntervalData(intervalData, range);
    }
</script>

{#if histogramData}
    <div class="graph intervals">
        <h1>Review Intervals</h1>

        <div class="range-box">
            <label>
                <input type="radio" bind:group={range} value={IntervalRange.Month} />
                Month
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile50} />
                50th percentile
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile95} />
                95th percentile
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile999} />
                99.9th percentile
            </label>
            <label>
                <input type="radio" bind:group={range} value={IntervalRange.All} />
                All
            </label>
        </div>

        <HistogramGraph
            data={histogramData}
            xText="Interval (days)"
            yText="Number of cards" />
    </div>
{/if}
