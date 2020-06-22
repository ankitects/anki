<script lang="typescript">
    import {
        gatherIntervalData,
        intervalGraph,
        IntervalUpdateFn,
        IntervalRange,
    } from "./intervals";
    import type { IntervalGraphData } from "./intervals";
    import { onMount } from "svelte";
    import pb from "../backend/proto";

    export let cards: pb.BackendProto.Card[] | null = null;

    let svg = null as HTMLElement | SVGElement | null;
    let updater = null as IntervalUpdateFn | null;

    onMount(() => {
        updater = intervalGraph(svg as SVGElement);
    });

    let range = IntervalRange.Percentile95;

    let graphData: IntervalGraphData;
    $: graphData = gatherIntervalData(cards!);
    $: if (updater) {
        updater(graphData, range);
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

    <svg bind:this={svg}>
        <path class="area" />
    </svg>
</div>
