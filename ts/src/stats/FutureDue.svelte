<script lang="typescript">
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
    import { HistogramData } from "./histogram-graph";
    import { defaultGraphBounds, GraphRange, RevlogRange } from "./graphs";
    import { gatherData, GraphData, buildHistogram } from "./future-due";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let graphData = null as GraphData | null;
    let histogramData = null as HistogramData | null;
    let backlog: boolean = true;
    let svg = null as HTMLElement | SVGElement | null;
    let graphRange: GraphRange = GraphRange.Month;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        histogramData = buildHistogram(graphData, graphRange, backlog, i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_FUTURE_DUE_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_FUTURE_DUE_SUBTITLE);
    const backlogLabel = i18n.tr(i18n.TR.STATISTICS_BACKLOG_CHECKBOX);
</script>

<div class="graph" id="graph-future-due">
    <h1>{title}</h1>

    <div class="range-box-inner">
        <label>
            <input type="checkbox" bind:checked={backlog} />
            {backlogLabel}
        </label>

        <GraphRangeRadios bind:graphRange {i18n} revlogRange={RevlogRange.All} />
    </div>

    <div class="subtitle">{subtitle}</div>

    <HistogramGraph data={histogramData} {i18n} />

</div>
