<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData } from "./ease";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import { I18n } from "../i18n";
    import { TableDatum } from "./graphs";
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

    <HistogramGraph data={histogramData} {i18n} />

    <TableData {i18n} {tableData} />
</div>
