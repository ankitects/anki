<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import { gatherData, prepareData } from "./ease";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import TableData from "./TableData.svelte";

    export let sourceData: GraphsResponse | null = null;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData: HistogramData | null = null;
    let tableData: TableDatum[] = [];

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(
            gatherData(sourceData),
            dispatch,
            $prefs.browserLinksSupported,
        );
    }

    const title = tr.statisticsCardEaseTitle();
    const subtitle = tr.statisticsCardEaseSubtitle();
</script>

{#if !(sourceData?.fsrs ?? false)}
    <Graph {title} {subtitle}>
        <HistogramGraph data={histogramData} />

        <TableData {tableData} />
    </Graph>
{/if}
