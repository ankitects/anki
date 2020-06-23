<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";

    import { gatherIntervalData, intervalGraph, IntervalRange } from "./intervals";
    import type { IntervalGraphData } from "./intervals";
    import { onMount } from "svelte";
    import pb from "../backend/proto";

    const bounds = defaultGraphBounds();

    export let data: pb.BackendProto.GraphsOut | null = null;

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
        <g
            class="x-ticks no-domain-line"
            transform={`translate(0,${bounds.height - bounds.marginBottom})`} />
        <g
            class="y-ticks no-domain-line"
            transform={`translate(${bounds.marginLeft}, 0)`} />
        <text
            class="axis-label"
            transform={`translate(${bounds.width / 2}, ${bounds.height - 5})`}>
            Interval (days)
        </text>
        <text
            class="axis-label y-axis-label"
            transform={`translate(${bounds.marginLeft / 3}, ${(bounds.height - bounds.marginBottom) / 2 + bounds.marginTop}) rotate(-180)`}>
            Number of cards
        </text>
    </svg>
</div>
