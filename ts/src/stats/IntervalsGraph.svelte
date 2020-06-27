<script lang="typescript">
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
    import { HistogramData } from "./histogram-graph";
    import {
        gatherIntervalData,
        IntervalRange,
        prepareIntervalData,
        IntervalGraphData,
    } from "./intervals";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let intervalData: IntervalGraphData | null = null;
    let histogramData = null as HistogramData | null;

    let svg = null as HTMLElement | SVGElement | null;
    let range = IntervalRange.Percentile95;

    $: if (sourceData) {
        console.log("gathering data");
        intervalData = gatherIntervalData(sourceData);
    }

    $: if (intervalData) {
        console.log("preparing data");
        histogramData = prepareIntervalData(intervalData, range);
    }

    const month = timeSpan(i18n, 1 * MONTH);
</script>

{#if histogramData}
    <div class="graph intervals">
        <h1>Review Intervals</h1>

        <div class="range-box-inner">
            <label>
                <input type="radio" bind:group={range} value={IntervalRange.Month} />
                {month}
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile50} />
                50%
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile95} />
                95%
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile999} />
                99.9%
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
