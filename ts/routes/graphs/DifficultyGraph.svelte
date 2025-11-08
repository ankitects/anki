<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import { DifficultyRange, gatherData, prepareData } from "./difficulty";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap, TableDatum } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import HistogramGraph from "./HistogramGraph.svelte";
    import TableData from "./TableData.svelte";
    import InputBox from "./InputBox.svelte";

    export let sourceData: GraphsResponse | null = null;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData: HistogramData | null = null;
    let tableData: TableDatum[] = [];
    let range = DifficultyRange.All;

    $: percentile = {
        [DifficultyRange.Percentile50]: 0.5,
        [DifficultyRange.Percentile95]: 0.95,
        [DifficultyRange.All]: 1,
    }[range];

    $: if (sourceData) {
        const data = gatherData(sourceData);

        console.log(data.eases);

        [histogramData, tableData] = prepareData(
            data,
            dispatch,
            $prefs.browserLinksSupported,
            1 - percentile,
        );
    }

    const title = tr.statisticsCardDifficultyTitle();
    const subtitle = tr.statisticsCardDifficultySubtitle2();
</script>

{#if sourceData?.fsrs}
    <Graph {title} {subtitle}>
        <InputBox>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={DifficultyRange.Percentile50}
                />
                50%
            </label>
            <label>
                <input
                    type="radio"
                    bind:group={range}
                    value={DifficultyRange.Percentile95}
                />
                95%
            </label>
            <label>
                <input type="radio" bind:group={range} value={DifficultyRange.All} />
                {tr.statisticsRangeAllTime()}
            </label>
        </InputBox>

        <HistogramGraph data={histogramData} />

        <TableData {tableData} />
    </Graph>
{/if}
