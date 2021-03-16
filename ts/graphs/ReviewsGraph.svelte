<script lang="typescript">
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";
    import TableData from "./TableData.svelte";
    import AxisTicks from "./AxisTicks.svelte";

    import { defaultGraphBounds, RevlogRange, GraphRange } from "./graph-helpers";
    import type { TableDatum } from "./graph-helpers";
    import { gatherData, renderReviews } from "./reviews";
    import type { GraphData } from "./reviews";
    import { graph, graphArea } from "./graph-styles";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let revlogRange: RevlogRange;
    export let i18n: I18n;

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
            showTime,
            i18n
        );
    }

    const title = i18n.tr(i18n.TR.STATISTICS_REVIEWS_TITLE);
    const time = i18n.tr(i18n.TR.STATISTICS_REVIEWS_TIME_CHECKBOX);

    let subtitle = "";
    $: if (showTime) {
        subtitle = i18n.tr(i18n.TR.STATISTICS_REVIEWS_TIME_SUBTITLE);
    } else {
        subtitle = i18n.tr(i18n.TR.STATISTICS_REVIEWS_COUNT_SUBTITLE);
    }
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <label> <input type="checkbox" bind:checked={showTime} /> {time} </label>

        <GraphRangeRadios bind:graphRange {i18n} {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        {#each [4, 3, 2, 1, 0] as i}
            <g class="bars{i}" />
        {/each}
        <path class={graphArea} />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>

    <TableData {i18n} {tableData} />
</Graph>
