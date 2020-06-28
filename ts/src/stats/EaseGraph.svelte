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
        histogramData = prepareData(gatherData(sourceData), i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_CARD_EASE_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_CARD_EASE_SUBTITLE);
</script>

{#if histogramData}
    <div class="graph">
        <h1>{title}</h1>

        <div class="subtitle">{subtitle}</div>

        <HistogramGraph data={histogramData} />
    </div>
{/if}
