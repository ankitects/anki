<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";

    import AxisTicks from "./AxisTicks.svelte";
    import Graph from "./Graph.svelte";
    import type { RevlogRange } from "./graph-helpers";
    import { defaultGraphBounds, GraphRange } from "./graph-helpers";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import HoverColumns from "./HoverColumns.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import { renderRetentionOverTime } from "./retention-over-time";

    export let sourceData: GraphsResponse | null = null;
    export let revlogRange: RevlogRange;
    let graphRange: GraphRange = GraphRange.ThreeMonths;

    const bounds = defaultGraphBounds();

    let svg: HTMLElement | SVGElement | null = null;

    $: if (sourceData) {
        renderRetentionOverTime(svg as SVGElement, bounds, sourceData, graphRange);
    }

    const title = tr.statisticsRetentionOverTimeTitle();
    const subtitle = tr.statisticsRetentionOverTimeSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <GraphRangeRadios bind:graphRange {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <path class="retention-actual" />
        <path class="retention-predicted" />
        <HoverColumns />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>

    <div class="legend">
        <span>
            <span class="swatch actual"></span>
            {tr.statisticsRetentionActual()}
        </span>
        <span>
            <span class="swatch predicted"></span>
            {tr.statisticsRetentionPredicted()}
        </span>
    </div>
</Graph>

<style lang="scss">
    .retention-actual,
    .retention-predicted {
        fill: none;
        stroke-width: 2;
        pointer-events: none;
    }
    .retention-actual {
        stroke: var(--state-review);
    }
    .retention-predicted {
        stroke: var(--state-learn);
        stroke-dasharray: 6 4;
    }
    .legend {
        display: flex;
        justify-content: center;
        gap: 1.5em;
        font-size: small;
    }
    .swatch {
        display: inline-block;
        width: 1.5em;
        height: 0;
        margin-inline-end: 0.4em;
        vertical-align: middle;
        border-top: 2px solid var(--state-review);
        &.predicted {
            border-top: 2px dashed var(--state-learn);
        }
    }
</style>
