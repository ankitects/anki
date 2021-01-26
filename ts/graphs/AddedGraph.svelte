<script lang="typescript">
    import { RevlogRange, GraphRange } from "./graph-helpers";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import type { I18n } from "anki/i18n";
    import type { HistogramData } from "./histogram-graph";
    import { gatherData, buildHistogram } from "./added";
    import type { GraphData } from "./added";
    import type pb from "anki/backend_proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";
    import { createEventDispatcher } from "svelte";
    import type { PreferenceStore } from "./preferences";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let preferences: PreferenceStore;

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
            i18n,
            dispatch,
            $browserLinksSupported
        );
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ADDED_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_ADDED_SUBTITLE);
</script>

<div class="graph" id="graph-added">
    <h1>{title}</h1>

    <div class="subtitle">{subtitle}</div>

    <div class="range-box-inner">
        <GraphRangeRadios bind:graphRange {i18n} revlogRange={RevlogRange.All} />
    </div>

    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</div>
