<script lang="typescript">
    import { HistogramData } from "./histogram-graph";
    import { gatherData, prepareData, GraphData, AddedRange } from "./added";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    let svg = null as HTMLElement | SVGElement | null;
    let histogramData = null as HistogramData | null;
    let range = AddedRange.Month;

    let addedData: GraphData | null = null;
    $: if (sourceData) {
        console.log("gathering data");
        addedData = gatherData(sourceData);
    }

    $: if (addedData) {
        console.log("preparing data");
        histogramData = prepareData(addedData, range);
    }
</script>

{#if histogramData}
    <div class="graph">
        <h1>Added</h1>

        <div class="range-box">
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Month} />
                Month
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Quarter} />
                3 months
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Year} />
                Year
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.AllTime} />
                All time
            </label>
        </div>

        <HistogramGraph data={histogramData} xText="Days" yText="Number of cards" />
    </div>
{/if}
