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
    import { renderRetentionOverTime, targetBand } from "./retention-over-time";

    export let sourceData: GraphsResponse | null = null;
    export let revlogRange: RevlogRange;
    let graphRange: GraphRange = GraphRange.ThreeMonths;

    const bounds = defaultGraphBounds();

    let svg: HTMLElement | SVGElement | null = null;

    $: if (sourceData) {
        renderRetentionOverTime(svg as SVGElement, bounds, sourceData, graphRange);
    }
    $: band = sourceData ? targetBand(sourceData) : undefined;

    const title = tr.statisticsRetentionOverTimeTitle();
    const subtitle = tr.statisticsRetentionOverTimeSubtitle();
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <GraphRangeRadios bind:graphRange {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <rect class="target-band" />
        <path class="retention-line" />
        <HoverColumns />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>

    {#if band}
        <div class="band-caption">
            <span class="swatch"></span>
            {tr.statisticsRetentionOverTimeTargetBand({
                min: String(Math.round(band.min * 100)),
                max: String(Math.round(band.max * 100)),
            })}
        </div>
    {/if}
</Graph>

<style lang="scss">
    .target-band {
        fill: #22c55e;
        fill-opacity: 0.15;
        pointer-events: none;
    }
    .band-caption {
        text-align: center;
        font-size: small;
    }
    .swatch {
        display: inline-block;
        width: 1em;
        height: 0.8em;
        margin-inline-end: 0.3em;
        vertical-align: middle;
        background: rgba(34, 197, 94, 0.25);
    }
    .retention-line {
        fill: none;
        stroke: var(--state-review);
        stroke-width: 2;
        pointer-events: none;
    }
</style>
