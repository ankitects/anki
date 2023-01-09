<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";

    import AxisTicks from "./AxisTicks.svelte";
    import CumulativeOverlay from "./CumulativeOverlay.svelte";
    import Graph from "./Graph.svelte";
    import type { RevlogRange } from "./graph-helpers";
    import { defaultGraphBounds, GraphRange } from "./graph-helpers";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import { renderHours } from "./hours";
    import HoverColumns from "./HoverColumns.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let revlogRange: RevlogRange;
    let graphRange: GraphRange = GraphRange.Year;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        renderHours(svg as SVGElement, bounds, sourceData, graphRange);
    }

    const title = tr.statisticsHoursTitle();
    const subtitle = tr.statisticsHoursSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <GraphRangeRadios bind:graphRange {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <CumulativeOverlay />
        <HoverColumns />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>
</Graph>
