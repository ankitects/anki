<script lang="typescript">
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";
    import { HistogramData } from "./histogram-graph";
    import { gatherData, buildHistogram, GraphData, AddedRange } from "./added";
    import pb from "../backend/proto";
    import HistogramGraph from "./HistogramGraph.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

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
        histogramData = buildHistogram(addedData, range);
    }

    const month = timeSpan(i18n, 1 * MONTH);
    const month3 = timeSpan(i18n, 3 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
</script>

{#if histogramData}
    <div class="graph">
        <h1>Added</h1>

        <div class="range-box-inner">
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Month} />
                {month}
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Quarter} />
                {month3}
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.Year} />
                {year}
            </label>
            <label>
                <input type="radio" bind:group={range} value={AddedRange.AllTime} />
                All time
            </label>
        </div>

        <HistogramGraph data={histogramData} xText="Days" yText="Number of cards" />
    </div>
{/if}
