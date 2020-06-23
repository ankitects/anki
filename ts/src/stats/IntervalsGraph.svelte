<script lang="typescript">
    import AxisLabels from "./AxisLabels.svelte";
    import AxisTicks from "./AxisTicks.svelte";

    import { defaultGraphBounds } from "./graphs";
    import { gatherIntervalData, intervalGraph, IntervalRange } from "./intervals";
    import type { IntervalGraphData } from "./intervals";
    import pb from "../backend/proto";

    export let data: pb.BackendProto.GraphsOut | null = null;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;
    let range = IntervalRange.Percentile95;

    let intervalData: IntervalGraphData | null = null;
    $: if (data) {
        console.log("gathering data");
        intervalData = gatherIntervalData(data);
    }

    $: if (intervalData) {
        intervalGraph(svg as SVGElement, bounds, intervalData, range);
    }
</script>

<div class="graph intervals">
    <h1>Review Intervals</h1>

    <div class="range-box">
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Month} />
            Month
        </label>
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Percentile50} />
            50th percentile
        </label>
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.Percentile95} />
            95th percentile
        </label>
        <label>
            <input
                type="radio"
                bind:group={range}
                value={IntervalRange.Percentile999} />
            99.9th percentile
        </label>
        <label>
            <input type="radio" bind:group={range} value={IntervalRange.All} />
            All
        </label>
    </div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <g class="hoverzone" />
        <path class="area" />
        <AxisTicks {bounds} />
        <AxisLabels {bounds} xText="Interval (days)" yText="Number of cards" />
    </svg>
</div>
