<script lang="typescript">
    import { HistogramData, histogramGraph } from "./histogram-graph";
    import AxisLabels from "./AxisLabels.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import { defaultGraphBounds, RevlogRange } from "./graphs";
    import { GraphData, gatherData, renderReviews, ReviewRange } from "./reviews";
    import pb from "../backend/proto";
    import { timeSpan, MONTH, YEAR } from "../time";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let revlogRange: RevlogRange = RevlogRange.Month;
    export let i18n: I18n;

    let graphData: GraphData | null = null;

    let bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;
    let range: ReviewRange;
    let showTime = false;

    $: switch (revlogRange as RevlogRange) {
        case RevlogRange.Month:
            range = ReviewRange.Month;
            break;
        case RevlogRange.Year:
            range = ReviewRange.Year;
            break;
        case RevlogRange.All:
            range = ReviewRange.AllTime;
            break;
    }

    const xText = "";
    const yText = "Times pressed";

    $: if (sourceData) {
        console.log("gathering data");
        graphData = gatherData(sourceData);
    }

    $: if (graphData) {
        renderReviews(svg as SVGElement, bounds, graphData, range, showTime);
    }

    const month = timeSpan(i18n, 1 * MONTH);
    const month3 = timeSpan(i18n, 3 * MONTH);
    const year = timeSpan(i18n, 1 * YEAR);
</script>

<div class="graph">
    <h1>Reviews</h1>

    <div class="range-box-inner">
        <label>
            <input type="checkbox" bind:checked={showTime} />
            Time
        </label>

        {#if revlogRange >= RevlogRange.Year}
            <label>
                <input type="radio" bind:group={range} value={ReviewRange.Month} />
                {month}
            </label>
            <label>
                <input type="radio" bind:group={range} value={ReviewRange.Quarter} />
                {month3}
            </label>
            <label>
                <input type="radio" bind:group={range} value={ReviewRange.Year} />
                {year}
            </label>
        {/if}
        {#if revlogRange === RevlogRange.All}
            <label>
                <input type="radio" bind:group={range} value={ReviewRange.AllTime} />
                All time
            </label>
        {/if}
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
