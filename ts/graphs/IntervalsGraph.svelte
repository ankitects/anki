<script lang="typescript">
    import { timeSpan, MONTH } from "anki/time";
    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";
    import { createEventDispatcher } from "svelte";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import HistogramGraph from "./HistogramGraph.svelte";
    import TableData from "./TableData.svelte";

    import type { HistogramData } from "./histogram-graph";
    import {
        gatherIntervalData,
        IntervalRange,
        prepareIntervalData,
    } from "./intervals";
    import type { IntervalGraphData } from "./intervals";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import type { PreferenceStore } from "./preferences";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let preferences: PreferenceStore;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let intervalData: IntervalGraphData | null = null;
    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let range = IntervalRange.Percentile95;
    let { browserLinksSupported } = preferences;

    $: if (sourceData) {
        intervalData = gatherIntervalData(sourceData);
    }

    $: if (intervalData) {
        [histogramData, tableData] = prepareIntervalData(
            intervalData,
            range,
            i18n,
            dispatch,
            $browserLinksSupported
        );
    }

    const title = i18n.tr(i18n.TR.STATISTICS_INTERVALS_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_INTERVALS_SUBTITLE);
    const month = timeSpan(i18n, 1 * MONTH);
    const all = i18n.tr(i18n.TR.STATISTICS_RANGE_ALL_TIME);
</script>

<Graph {title} {subtitle}>
    <InputBox>
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
            <input type="radio" bind:group={range} value={IntervalRange.All} />
            {all}
        </label>
    </InputBox>

    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</Graph>
