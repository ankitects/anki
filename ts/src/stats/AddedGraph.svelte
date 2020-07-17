<script lang="typescript">
    import { RevlogRange, GraphRange } from "./graphs";
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
    import { HistogramData } from "./histogram-graph";
    import { gatherData, buildHistogram, GraphData } from "./added";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let svg = null as HTMLElement | SVGElement | null;
    let histogramData = null as HistogramData | null;
    let graphRange: GraphRange = GraphRange.Month;

    let addedData: GraphData | null = null;
    $: if (sourceData) {
        addedData = gatherData(sourceData);
    }

    $: if (addedData) {
        histogramData = buildHistogram(addedData, graphRange, i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ADDED_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_ADDED_SUBTITLE);
</script>

<div class="graph" id="graph-added">
    <h1>{title}</h1>

    <div class="range-box-inner">
        <GraphRangeRadios bind:graphRange {i18n} revlogRange={RevlogRange.All} />
    </div>

    <div class="subtitle">{subtitle}</div>

    <HistogramGraph data={histogramData} {i18n} />
</div>
