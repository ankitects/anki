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
    import { writable, type Writable } from "svelte/store";
    import InputBox from "../graphs/InputBox.svelte";
    import {
        renderForgettingCurve,
        TimeRange,
        calculateMaxDays,
        filterRevlog,
    } from "./forgetting-curve";
    import { defaultGraphBounds } from "../graphs/graph-helpers";
    import HoverColumns from "../graphs/HoverColumns.svelte";

    export let revlog: RevlogEntry[];
    export let desiredRetention: number;
    export let decay: number;
    let svg: HTMLElement | SVGElement | null = null;
    const bounds = defaultGraphBounds();
    const title = tr.cardStatsFsrsForgettingCurveTitle();

    $: filteredRevlog = filterRevlog(revlog);
    $: maxDays = calculateMaxDays(filteredRevlog, TimeRange.AllTime);

    let defaultTimeRange = TimeRange.Week;
    const timeRange: Writable<TimeRange> = writable(defaultTimeRange);

    $: if (maxDays > 365) {
        defaultTimeRange = TimeRange.AllTime;
    } else if (maxDays > 30) {
        defaultTimeRange = TimeRange.Year;
    } else if (maxDays > 7) {
        defaultTimeRange = TimeRange.Month;
    }

    $: $timeRange = defaultTimeRange;

    $: renderForgettingCurve(
        filteredRevlog,
        $timeRange,
        svg as SVGElement,
        bounds,
        desiredRetention,
        decay,
    );
</script>

<div class="forgetting-curve">
    {#if maxDays > 7}
        <InputBox>
            <div class="time-range-selector">
                <label>
                    <input
                        type="radio"
                        bind:group={$timeRange}
                        value={TimeRange.Week}
                    />
                    {tr.cardStatsFsrsForgettingCurveFirstWeek()}
                </label>
                <label>
                    <input
                        type="radio"
                        bind:group={$timeRange}
                        value={TimeRange.Month}
                    />
                    {tr.cardStatsFsrsForgettingCurveFirstMonth()}
                </label>
                {#if maxDays > 30}
                    <label>
                        <input
                            type="radio"
                            bind:group={$timeRange}
                            value={TimeRange.Year}
                        />
                        {tr.cardStatsFsrsForgettingCurveFirstYear()}
                    </label>
                {/if}
                {#if maxDays > 365}
                    <label>
                        <input
                            type="radio"
                            bind:group={$timeRange}
                            value={TimeRange.AllTime}
                        />
                        {tr.cardStatsFsrsForgettingCurveAllTime()}
                    </label>
                {/if}
            </div>
        </InputBox>
    {/if}
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
        margin-bottom: 1em;
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
