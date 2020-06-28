<script lang="typescript">
    import { HistogramData, histogramGraph } from "./histogram-graph";
    import AxisLabels from "./AxisLabels.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import { defaultGraphBounds } from "./graphs";
    import { I18n } from "../i18n";

    export let data: HistogramData | null = null;
    export let i18n: I18n;
    export let xText: string;
    export let yText: string;

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
    <AxisLabels {bounds} {xText} {yText} {i18n} />
</svg>
