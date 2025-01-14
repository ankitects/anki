<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import type { GraphData } from "./future-due";
    import { buildHistogram, gatherData } from "./future-due";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import { GraphRange, RevlogRange } from "./graph-helpers";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import InputBox from "./InputBox.svelte";
    import TableData from "./TableData.svelte";

    export let sourceData: GraphsResponse | null = null;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let graphData: GraphData | null = null;
    let histogramData: HistogramData | null = null;
    let tableData: TableDatum[] = [];
    let graphRange: GraphRange = GraphRange.Month;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        ({ histogramData, tableData } = buildHistogram(
            graphData,
            graphRange,
            $prefs.futureDueShowBacklog,
            dispatch,
            $prefs.browserLinksSupported,
        ));
    }

    const title = tr.statisticsFutureDueTitle();
    const subtitle = tr.statisticsFutureDueSubtitle();
    const backlogLabel = tr.statisticsBacklogCheckbox();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        {#if graphData && graphData.haveBacklog}
            <label>
                <input type="checkbox" bind:checked={$prefs.futureDueShowBacklog} />
                {backlogLabel}
            </label>
        {/if}

        <GraphRangeRadios bind:graphRange revlogRange={RevlogRange.All} />
    </InputBox>

    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
