<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";

    import type { HistogramData } from "./histogram-graph";
    import { GraphRange, RevlogRange } from "./graph-helpers";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import { gatherData, buildHistogram } from "./future-due";
    import type { GraphData } from "./future-due";
    import type { PreferenceStore } from "./preferences";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let preferences: PreferenceStore;

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
            i18n,
            dispatch,
            $browserLinksSupported
        ));
    }

    const title = i18n.statisticsFutureDueTitle();
    const subtitle = i18n.statisticsFutureDueSubtitle();
    const backlogLabel = i18n.statisticsBacklogCheckbox();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        {#if graphData && graphData.haveBacklog}
            <label>
                <input type="checkbox" bind:checked={$futureDueShowBacklog} />
                {backlogLabel}
            </label>
        {/if}

        <GraphRangeRadios bind:graphRange {i18n} revlogRange={RevlogRange.All} />
    </InputBox>

    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</Graph>
