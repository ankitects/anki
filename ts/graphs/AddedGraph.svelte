<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";
    import { createEventDispatcher } from "svelte";

    import type { PreferenceStore } from "../sveltelib/preferences";
    import type { GraphData } from "./added";
    import { buildHistogram, gatherData } from "./added";
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

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let graphRange: GraphRange = GraphRange.Month;
    const { browserLinksSupported } = preferences;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let addedData: GraphData | null = null;
    $: if (sourceData) {
        addedData = gatherData(sourceData);
    }

    $: if (addedData) {
        [histogramData, tableData] = buildHistogram(
            addedData,
            graphRange,
            dispatch,
            $browserLinksSupported,
        );
    }

    const title = tr.statisticsAddedTitle();
    const subtitle = tr.statisticsAddedSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <GraphRangeRadios bind:graphRange revlogRange={RevlogRange.All} />
    </InputBox>

    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
