<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { timeSpan, MONTH } from "lib/time";

    import type pb from "lib/backend_proto";
    import type { PreferenceStore } from "sveltelib/preferences";
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

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    import * as tr from "lib/i18n";
    export let preferences: PreferenceStore<pb.BackendProto.GraphPreferences>;

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
            dispatch,
            $browserLinksSupported
        );
    }

    const title = tr.statisticsIntervalsTitle();
    const subtitle = tr.statisticsIntervalsSubtitle();
    const month = timeSpan(1 * MONTH);
    const all = tr.statisticsRangeAllTime();
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

    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
