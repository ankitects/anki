<script lang="typescript">
    import { timeSpan, MONTH } from "../time";
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
    import { TableDatum } from "./graphs";
    import TableData from "./TableData.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let intervalData: IntervalGraphData | null = null;
    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let range = IntervalRange.Percentile95;

    $: if (sourceData) {
        intervalData = gatherIntervalData(sourceData);
    }

    $: if (intervalData) {
        [histogramData, tableData] = prepareIntervalData(intervalData, range, i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_INTERVALS_TITLE);
    const month = timeSpan(i18n, 1 * MONTH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_INTERVALS_SUBTITLE);
</script>

<div class="graph intervals" id="graph-intervals">
    <h1>{title}</h1>

    <div class="range-box-inner">
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Month} />
            {month}
        </label>
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Percentile50} />
            50%
        </label>
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Percentile95} />
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
            {all}
        </label>
    </div>

    <div class="subtitle">{subtitle}</div>

    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</div>
