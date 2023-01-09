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
    import type { RevlogRange, TableDatum } from "./graph-helpers";
    import { defaultGraphBounds, GraphRange } from "./graph-helpers";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import HoverColumns from "./HoverColumns.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import type { GraphData } from "./reviews";
    import { gatherData, renderReviews } from "./reviews";
    import TableData from "./TableData.svelte";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let revlogRange: RevlogRange;

    let graphData: GraphData | null = null;

    const bounds = defaultGraphBounds();
    let svg = null as HTMLElement | SVGElement | null;
    let graphRange: GraphRange = GraphRange.Month;
    let showTime = false;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    let tableData: TableDatum[] = [] as any;
    $: if (graphData) {
        tableData = renderReviews(
            svg as SVGElement,
            bounds,
            graphData,
            graphRange,
            showTime,
        );
    }

    const title = tr.statisticsReviewsTitle();
    const time = tr.statisticsReviewsTimeCheckbox();

    let subtitle = "";
    $: if (showTime) {
        subtitle = tr.statisticsReviewsTimeSubtitle();
    } else {
        subtitle = tr.statisticsReviewsCountSubtitle();
    }
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <label> <input type="checkbox" bind:checked={showTime} /> {time} </label>

        <GraphRangeRadios bind:graphRange {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        {#each [4, 3, 2, 1, 0] as i}
            <g class="bars{i}" />
        {/each}
        <CumulativeOverlay />
        <HoverColumns />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} />
    </svg>

    <TableData {tableData} />
</Graph>
