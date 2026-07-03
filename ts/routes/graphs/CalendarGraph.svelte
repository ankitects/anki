<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import AxisTicks from "./AxisTicks.svelte";
    import type { GraphData } from "./calendar";
    import { gatherData, renderCalendar } from "./calendar";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs, SearchEventMap } from "./graph-helpers";
    import { defaultGraphBounds, RevlogRange } from "./graph-helpers";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    export let sourceData: GraphsResponse;
    export let prefs: GraphPrefs;
    export let revlogRange: RevlogRange;
    export let nightMode: boolean;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let graphData: GraphData | null = null;

    const bounds = defaultGraphBounds();
    bounds.height = 120;
    bounds.marginLeft = 20;
    bounds.marginRight = 20;

    let svg: HTMLElement | SVGElement | null = null;
    const maxYear = new Date().getFullYear();
    let minYear = 0;
    let targetYear = maxYear;

    $: if (sourceData) {
        graphData = gatherData(sourceData, $prefs.calendarFirstDayOfWeek);
        renderCalendar(
            svg as SVGElement,
            bounds,
            graphData,
            dispatch,
            targetYear,
            nightMode,
            revlogRange,
            (day) => ($prefs.calendarFirstDayOfWeek = day),
        );
    }

    $: {
        if (revlogRange < RevlogRange.Year) {
            minYear = maxYear;
        } else if (revlogRange === RevlogRange.Year) {
            minYear = maxYear - 1;
        } else {
            minYear = 2000;
        }
        if (targetYear < minYear) {
            targetYear = minYear;
        }
    }

    const title = tr.statisticsCalendarTitle();
</script>

<Graph {title}>
    <InputBox>
        <span>
            <button on:click={() => targetYear--} disabled={minYear >= targetYear}>
                ◄
            </button>
        </span>
        <span>{targetYear}</span>
        <span>
            <button on:click={() => targetYear++} disabled={targetYear >= maxYear}>
                ►
            </button>
        </span>
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="weekdays" />
        <g class="days" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>
</Graph>
