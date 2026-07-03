<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import { gatherData, prepareData } from "./difficulty";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import TableData from "./TableData.svelte";
    import PercentageRange from "./PercentageRange.svelte";
    import { PercentageRangeEnum, PercentageRangeToQuantile } from "./percentageRange";

    export let sourceData: GraphsResponse | null = null;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData: HistogramData | null = null;
    let tableData: TableDatum[] = [];
    let range = PercentageRangeEnum.All;

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(
            gatherData(sourceData),
            dispatch,
            $prefs.browserLinksSupported,
            PercentageRangeToQuantile(range),
        );
    }

    const title = tr.statisticsCardDifficultyTitle();
    const subtitle = tr.statisticsCardDifficultySubtitle2();
</script>

{#if sourceData?.fsrs}
    <Graph {title} {subtitle}>
        <PercentageRange bind:range />

        <HistogramGraph data={histogramData} />

        <TableData {tableData} />
    </Graph>
{/if}
