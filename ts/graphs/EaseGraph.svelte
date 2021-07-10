<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Stats } from "lib/proto";
    import * as tr from "lib/i18n";
    import type { PreferenceStore } from "sveltelib/preferences";

    import { createEventDispatcher } from "svelte";

    import HistogramGraph from "./HistogramGraph.svelte";
    import Graph from "./Graph.svelte";
    import TableData from "./TableData.svelte";

    import type { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData } from "./ease";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let { browserLinksSupported } = preferences;

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(
            gatherData(sourceData),
            dispatch,
            $browserLinksSupported
        );
    }

    const title = tr.statisticsCardEaseTitle();
    const subtitle = tr.statisticsCardEaseSubtitle();
</script>

<Graph {title} {subtitle}>
    <HistogramGraph data={histogramData} />

    <TableData {tableData} />
</Graph>
