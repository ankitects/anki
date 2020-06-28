<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData, GraphData } from "./ease";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let svg = null as HTMLElement | SVGElement | null;
    let histogramData = null as HistogramData | null;

    $: if (sourceData) {
        histogramData = prepareData(gatherData(sourceData));
    }

    const title = i18n.tr(i18n.TR.STATISTICS_CARD_EASE_TITLE);
    const yText = i18n.tr(i18n.TR.STATISTICS_AXIS_LABEL_CARD_COUNT);
</script>

{#if histogramData}
    <div class="graph">
        <h1>{title}</h1>

        <HistogramGraph data={histogramData} xText="Ease (%)" {yText} {i18n} />
    </div>
{/if}
