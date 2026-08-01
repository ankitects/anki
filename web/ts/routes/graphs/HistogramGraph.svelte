<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import AxisTicks from "./AxisTicks.svelte";
    import CumulativeOverlay from "./CumulativeOverlay.svelte";
    import { defaultGraphBounds } from "./graph-helpers";
    import type { HistogramData } from "./histogram-graph";
    import { histogramGraph } from "./histogram-graph";
    import HoverColumns from "./HoverColumns.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    export let data: HistogramData | null = null;

    const bounds = defaultGraphBounds();
    let svg: HTMLElement | SVGElement | null = null;

    $: histogramGraph(svg as SVGElement, bounds, data);
</script>

<svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
    <g class="bars" />
    <HoverColumns />
    <CumulativeOverlay />
    <AxisTicks {bounds} />
    <NoDataOverlay {bounds} />
</svg>
