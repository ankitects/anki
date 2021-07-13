<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";

    import type { Stats } from "lib/proto";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";
    import type { PreferenceStore } from "sveltelib/preferences";

    import type { HistogramData } from "./histogram-graph";
    import { GraphRange, RevlogRange } from "./graph-helpers";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import { gatherData, buildHistogram } from "./future-due";
    import type { GraphData } from "./future-due";

    export let sourceData: Stats.GraphsResponse | null = null;
    import * as tr from "lib/i18n";
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let graphData = null as GraphData | null;
    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [] as any;
    let graphRange: GraphRange = GraphRange.Month;
    let { browserLinksSupported, futureDueShowBacklog } = preferences;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        ({ histogramData, tableData } = buildHistogram(
            graphData,
            graphRange,
            $futureDueShowBacklog,
            dispatch,
            $browserLinksSupported
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
