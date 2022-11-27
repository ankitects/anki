<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";
    import { createEventDispatcher } from "svelte";

    import type { PreferenceStore } from "../sveltelib/preferences";
    import { gatherData, prepareData } from "./ease";
    import Graph from "./Graph.svelte";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import TableData from "./TableData.svelte";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    const { browserLinksSupported } = preferences;

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(
            gatherData(sourceData),
            dispatch,
            $browserLinksSupported,
        );
    }

    const title = tr.statisticsCardEaseTitle();
    const subtitle = tr.statisticsCardEaseSubtitle();
</script>

<Graph {title} {subtitle}>
    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
