<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import type { Stats } from "lib/proto";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import CumulativeOverlay from "./CumulativeOverlay.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import HoverColumns from "./HoverColumns.svelte";

    import { defaultGraphBounds, RevlogRange, GraphRange } from "./graph-helpers";
    import type { TableDatum } from "./graph-helpers";
    import { gatherData, renderReviews } from "./reviews";
    import type { GraphData } from "./reviews";

    export let sourceData: Stats.GraphsResponse | null = null;
    export let revlogRange: RevlogRange;
    import * as tr from "lib/i18n";

    let graphData: GraphData | null = null;

    let bounds = defaultGraphBounds();
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
            showTime
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
