<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";
    import { createEventDispatcher } from "svelte";

    import type { PreferenceStore } from "../sveltelib/preferences";
    import type { GraphData } from "./future-due";
    import { buildHistogram, gatherData } from "./future-due";
    import Graph from "./Graph.svelte";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import { GraphRange, RevlogRange } from "./graph-helpers";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import InputBox from "./InputBox.svelte";
    import TableData from "./TableData.svelte";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let graphData = null as GraphData | null;
    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [] as any;
    let graphRange: GraphRange = GraphRange.Month;
    const { browserLinksSupported, futureDueShowBacklog } = preferences;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        ({ histogramData, tableData } = buildHistogram(
            graphData,
            graphRange,
            $futureDueShowBacklog,
            dispatch,
            $browserLinksSupported,
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
                <input type="checkbox" bind:checked={$futureDueShowBacklog} />
                {backlogLabel}
            </label>
        {/if}

        <GraphRangeRadios bind:graphRange revlogRange={RevlogRange.All} />
    </InputBox>

    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
