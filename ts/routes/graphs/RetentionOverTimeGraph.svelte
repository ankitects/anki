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
        <rect class="band-above" />
        <rect class="band-target" />
        <rect class="band-below" />
        <path class="retention-line" />
        <HoverColumns />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>

    {#if band}
        <div class="band-key">
            <div>
                <span class="swatch above"></span>
                {tr.statisticsRetentionOverTimeAboveTarget()}
            </div>
            <div>
                <span class="swatch target"></span>
                {tr.statisticsRetentionOverTimeTargetBand({
                    min: String(Math.round(band.min * 100)),
                    max: String(Math.round(band.max * 100)),
                })}
            </div>
            <div>
                <span class="swatch below"></span>
                {tr.statisticsRetentionOverTimeBelowTarget()}
            </div>
        </div>
    {/if}
</Graph>

<style lang="scss">
    $above: #eab308;
    $target: #22c55e;
    $below: #ef4444;

    .band-above,
    .band-target,
    .band-below {
        fill-opacity: 0.15;
        pointer-events: none;
    }
    .band-above {
        fill: $above;
    }
    .band-target {
        fill: $target;
    }
    .band-below {
        fill: $below;
    }
    .band-key {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        width: fit-content;
        margin: 0 auto;
        gap: 0.2em;
        font-size: small;
    }
    .swatch {
        display: inline-block;
        width: 1em;
        height: 0.8em;
        margin-inline-end: 0.3em;
        vertical-align: middle;
        &.above {
            background: rgba($above, 0.3);
        }
        &.target {
            background: rgba($target, 0.3);
        }
        &.below {
            background: rgba($below, 0.3);
        }
    }
    .retention-line {
        fill: none;
        stroke: var(--state-review);
        stroke-width: 2;
        pointer-events: none;
    }
</style>
