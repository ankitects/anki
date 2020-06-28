<script lang="typescript">
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
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
    export let i18n: I18n;

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

    const title = i18n.tr(i18n.TR.STATISTICS_FUTURE_DUE_TITLE);
    const month = timeSpan(i18n, 1 * MONTH);
    const month3 = timeSpan(i18n, 3 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
</script>

{#if histogramData}

    <div class="graph">
        <h1>{title}</h1>

        <div class="range-box-inner">
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Month} />
                {month}
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Quarter} />
                {month3}
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.Year} />
                {year}
            </label>
            <label>
                <input type="radio" bind:group={range} value={FutureDueRange.AllTime} />
                {all}
            </label>
        </div>

        <HistogramGraph
            data={histogramData}
            xText="Days from now"
            yText="Number of cards" />

    </div>
{/if}
