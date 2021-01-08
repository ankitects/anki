<script lang="typescript">
    import type { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData } from "./ease";
    import type pb from "anki/backend_proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import type { I18n } from "anki/i18n";
    import type { TableDatum } from "./graph-helpers";
    import TableData from "./TableData.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let histogramData = null as HistogramData | null;
    let tableData: TableDatum[] = [];

    $: if (sourceData) {
        [histogramData, tableData] = prepareData(gatherData(sourceData), i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_CARD_EASE_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_CARD_EASE_SUBTITLE);
</script>

<div class="graph" id="graph-ease">
    <h1>{title}</h1>

    <div class="subtitle">{subtitle}</div>

    <HistogramGraph data={histogramData} {i18n} on:search />

    <TableData {i18n} {tableData} />
</div>
