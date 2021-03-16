<script lang="typescript">
    import type { I18n } from "anki/i18n";

    import Graph from "./Graph.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    import type { HistogramData } from "./histogram-graph";
    import { histogramGraph } from "./histogram-graph";
    import { defaultGraphBounds } from "./graph-helpers";
    import { graphArea, graphHoverzone } from "./graph-styles";

    export let data: HistogramData | null = null;
    export let i18n: I18n;

    let bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;

    $: histogramGraph(svg as SVGElement, bounds, data);
</script>

<svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
    <g class="bars" />
    <g class={graphHoverzone} />
    <path class={graphArea} />
    <AxisTicks {bounds} />
    <NoDataOverlay {bounds} {i18n} />
</svg>
