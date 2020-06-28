<script lang="typescript">
    import { HistogramData, histogramGraph } from "./histogram-graph";
    import AxisTicks from "./AxisTicks.svelte";
    import { defaultGraphBounds } from "./graphs";

    export let data: HistogramData | null = null;

    let bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;

    $: if (data) {
        histogramGraph(svg as SVGElement, bounds, data);
    }
</script>

<svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
    <g class="bars" />
    <g class="hoverzone" />
    <path class="area" />
    <AxisTicks {bounds} />
</svg>
