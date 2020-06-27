<script lang="typescript">
    import { HistogramData, histogramGraph } from "./histogram-graph";
    import AxisLabels from "./AxisLabels.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import { defaultGraphBounds } from "./graphs";
    import { GraphData, gatherData, renderReviews, ReviewRange } from "./reviews";
    import pb from "../backend/proto";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    let graphData: GraphData | null = null;

    let bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;
    let range = ReviewRange.Month;
    let showTime = false;

    const xText = "";
    const yText = "Times pressed";

    $: if (sourceData) {
        console.log("gathering data");
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        renderReviews(svg as SVGElement, bounds, graphData, range, showTime);
    }
</script>

<div class="graph">
    <h1>Reviews</h1>

    <div class="range-box-inner">
        <label>
            <input type="checkbox" bind:checked={showTime} />
            Time
        </label>

        <label>
            <input type="radio" bind:group={range} value={ReviewRange.Month} />
            Month
        </label>
        <label>
            <input type="radio" bind:group={range} value={ReviewRange.Quarter} />
            3 months
        </label>
        <label>
            <input type="radio" bind:group={range} value={ReviewRange.Year} />
            Year
        </label>
        <label>
            <input type="radio" bind:group={range} value={ReviewRange.AllTime} />
            All time
        </label>
    </div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        {#each [4, 3, 2, 1, 0] as i}
            <g class="bars{i}" />
        {/each}
        <path class="area" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <AxisLabels {bounds} {xText} {yText} />
    </svg>

</div>
