<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData, GraphData } from "./ease";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    let svg = null as HTMLElement | SVGElement | null;
    let histogramData = null as HistogramData | null;

    $: if (sourceData) {
        console.log("gathering data");
        histogramData = prepareData(gatherData(sourceData));
    }
</script>

{#if histogramData}
    <div class="graph">
        <h1>Card Ease</h1>

        <HistogramGraph data={histogramData} xText="Ease (%)" yText="Number of cards" />
    </div>
{/if}
