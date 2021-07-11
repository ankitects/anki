<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Stats } from "lib/proto";
    import type { PreferenceStore } from "sveltelib/preferences";
    import { createEventDispatcher } from "svelte";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";

    import { RevlogRange, GraphRange } from "./graph-helpers";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import { gatherData, buildHistogram } from "./added";
    import type { GraphData } from "./added";

    export let sourceData: Stats.GraphsResponse | null = null;
    import * as tr from "lib/i18n";
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let graphRange: GraphRange = GraphRange.Month;
    let { browserLinksSupported } = preferences;

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
            $browserLinksSupported
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
