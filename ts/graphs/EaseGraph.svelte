<script lang="typescript">
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";
    import { createEventDispatcher } from "svelte";

    import HistogramGraph from "./HistogramGraph.svelte";
    import Graph from "./Graph.svelte";
    import TableData from "./TableData.svelte";

    import type { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData } from "./ease";
    import type { TableDatum, SearchEventMap } from "./graph-helpers";
    import type { PreferenceStore } from "./preferences";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let preferences: PreferenceStore;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];
    let { browserLinksSupported } = preferences;

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(
            gatherData(sourceData),
            i18n,
            dispatch,
            $browserLinksSupported
        );
    }

    const title = i18n.statisticsCardEaseTitle();
    const subtitle = i18n.statisticsCardEaseSubtitle();
</script>

<Graph {title} {subtitle}>
    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</Graph>
