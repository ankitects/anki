<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { type CardStatsResponse_StatsRevlogEntry as RevlogEntry } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import Graph from "../graphs/Graph.svelte";
    import NoDataOverlay from "../graphs/NoDataOverlay.svelte";
    import AxisTicks from "../graphs/AxisTicks.svelte";
    import { writable } from "svelte/store";
    import InputBox from "../graphs/InputBox.svelte";
    import { prepareData, renderForgettingCurve, TimeRange } from "./forgetting-curve";
    import { defaultGraphBounds } from "../graphs/graph-helpers";
    import HoverColumns from "../graphs/HoverColumns.svelte";

    export let revlog: RevlogEntry[];
    let svg = null as HTMLElement | SVGElement | null;
    const bounds = defaultGraphBounds();
    const timeRange = writable(TimeRange.AllTime);
    const title = tr.cardStatsFsrsForgettingCurveTitle();
    const data = prepareData(revlog, TimeRange.AllTime);

    $: renderForgettingCurve(revlog, $timeRange, svg as SVGElement, bounds);
</script>

<div class="forgetting-curve">
    <InputBox>
        <div class="time-range-selector">
            <label>
                <input type="radio" bind:group={$timeRange} value={TimeRange.Week} />
                {tr.cardStatsFsrsForgettingCurveFirstWeek()}
            </label>
            <label>
                <input type="radio" bind:group={$timeRange} value={TimeRange.Month} />
                {tr.cardStatsFsrsForgettingCurveFirstMonth()}
            </label>
            {#if data.length > 0 && data.some((point) => point.daysSinceFirstLearn > 365)}
                <label>
                    <input
                        type="radio"
                        bind:group={$timeRange}
                        value={TimeRange.Year}
                    />
                    {tr.cardStatsFsrsForgettingCurveFirstYear()}
                </label>
            {/if}
            <label>
                <input type="radio" bind:group={$timeRange} value={TimeRange.AllTime} />
                {tr.cardStatsFsrsForgettingCurveAllTime()}
            </label>
        </div>
    </InputBox>
    <Graph {title}>
        <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
            <AxisTicks {bounds} />
            <HoverColumns />
            <NoDataOverlay {bounds} />
        </svg>
    </Graph>
</div>

<style>
    .forgetting-curve {
        width: 100%;
        max-width: 50em;
    }

    .time-range-selector {
        display: flex;
        justify-content: space-around;
        margin-bottom: 1em;
        width: 100%;
        max-width: 50em;
    }

    .time-range-selector label {
        display: flex;
        align-items: center;
    }

    .time-range-selector input {
        margin-right: 0.5em;
    }
</style>
