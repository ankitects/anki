<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import AxisTicks from "./AxisTicks.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import CumulativeOverlay from "./CumulativeOverlay.svelte";
    import HoverColumns from "./HoverColumns.svelte";

    import type { HistogramData } from "./histogram-graph";
    import { histogramGraph } from "./histogram-graph";
    import { defaultGraphBounds } from "./graph-helpers";

    export let data: HistogramData | null = null;

    let bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;

    $: histogramGraph(svg as SVGElement, bounds, data);
</script>

<svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
    <g class="bars" />
    <HoverColumns />
    <CumulativeOverlay />
    <AxisTicks {bounds} />
    <NoDataOverlay {bounds} />
</svg>
