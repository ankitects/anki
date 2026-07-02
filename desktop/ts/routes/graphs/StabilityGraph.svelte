<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { MONTH, timeSpan } from "@tslib/time";
    import { createEventDispatcher } from "svelte";

    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import InputBox from "./InputBox.svelte";
    import type { IntervalGraphData } from "./intervals";
    import {
        gatherIntervalData,
        IntervalRange,
        prepareIntervalData,
    } from "./intervals";
    import TableData from "./TableData.svelte";

    export let sourceData: GraphsResponse | null = null;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let intervalData: IntervalGraphData | null = null;
    let histogramData: HistogramData | null = null;
    let tableData: TableDatum[] = [];
    let range = IntervalRange.Percentile95;

    $: if (sourceData) {
        intervalData = gatherIntervalData(sourceData.stability!);
    }

    $: if (intervalData) {
        [histogramData, tableData] = prepareIntervalData(
            intervalData,
            range,
            dispatch,
            $prefs.browserLinksSupported,
            true,
        );
    }

    const title = tr.statisticsCardStabilityTitle();
    const subtitle = tr.statisticsCardStabilitySubtitle();
    const month = timeSpan(1 * MONTH);
    const all = tr.statisticsRangeAllTime();
</script>

{#if sourceData?.fsrs}
    <Graph {title} {subtitle}>
        <InputBox>
            <label>
                <input type="radio" bind:group={range} value={IntervalRange.Month} />
                {month}
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile50}
                />
                50%
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={IntervalRange.Percentile95}
                />
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
{/if}
