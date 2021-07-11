<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Stats } from "lib/proto";
    import type { PreferenceStore } from "sveltelib/preferences";

    import { createEventDispatcher } from "svelte";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import AxisTicks from "./AxisTicks.svelte";

    import { defaultGraphBounds, RevlogRange } from "./graph-helpers";
    import type { SearchEventMap } from "./graph-helpers";
    import { gatherData, renderCalendar } from "./calendar";
    import type { GraphData } from "./calendar";

    export let sourceData: Stats.GraphsResponse;
    export let preferences: PreferenceStore<Stats.GraphPreferences>;
    export let revlogRange: RevlogRange;
    import * as tr from "lib/i18n";
    export let nightMode: boolean;

    let { calendarFirstDayOfWeek } = preferences;
    const dispatch = createEventDispatcher<SearchEventMap>();

    let graphData: GraphData | null = null;

    let bounds = defaultGraphBounds();
    bounds.height = 120;
    bounds.marginLeft = 20;
    bounds.marginRight = 20;

    let svg = null as HTMLElement | SVGElement | null;
    let maxYear = new Date().getFullYear();
    let minYear = 0;
    let targetYear = maxYear;

    $: if (sourceData) {
        graphData = gatherData(sourceData, $calendarFirstDayOfWeek);
    }

    $: {
        if (revlogRange < RevlogRange.Year) {
            minYear = maxYear;
        } else if ((revlogRange as RevlogRange) === RevlogRange.Year) {
            minYear = maxYear - 1;
        } else {
            minYear = 2000;
        }
        if (targetYear < minYear) {
            targetYear = minYear;
        }
    }

    $: if (graphData) {
        renderCalendar(
            svg as SVGElement,
            bounds,
            graphData,
            dispatch,
            targetYear,
            nightMode,
            revlogRange,
            calendarFirstDayOfWeek.set
        );
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
