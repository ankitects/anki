<script lang="typescript">
    import { RevlogRange } from "./graphs";
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
    import { HistogramData } from "./histogram-graph";
    import { gatherData, buildHistogram, GraphData, AddedRange } from "./added";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let svg = null as HTMLElement | SVGElement | null;
    let histogramData = null as HistogramData | null;
    let range: AddedRange = AddedRange.Month;

    let addedData: GraphData | null = null;
    $: if (sourceData) {
        addedData = gatherData(sourceData);
    }

    $: if (addedData) {
        histogramData = buildHistogram(addedData, range, i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ADDED_TITLE);
    const month = timeSpan(i18n, 1 * MONTH);
    const month3 = timeSpan(i18n, 3 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_ADDED_SUBTITLE);
</script>

<div class="graph">
    <h1>{title}</h1>

    <div class="range-box-inner">
        <label>
            <input type="radio" bind:group={range} value={AddedRange.Month} />
            {month}
        </label>
        <label>
            <input type="radio" bind:group={range} value={AddedRange.ThreeMonths} />
            {month3}
        </label>
        <label>
            <input type="radio" bind:group={range} value={AddedRange.Year} />
            {year}
        </label>
        <label>
            <input type="radio" bind:group={range} value={AddedRange.AllTime} />
            {all}
        </label>
    </div>

    <div class="subtitle">{subtitle}</div>

    <HistogramGraph data={histogramData} {i18n} />
</div>
