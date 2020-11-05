<script lang="typescript">
    import { defaultGraphBounds, RevlogRange, GraphRange } from "./graph-helpers";
    import AxisTicks from "./AxisTicks.svelte";
    import { renderHours } from "./hours";
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let revlogRange: RevlogRange;
    let graphRange: GraphRange = GraphRange.Year;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        renderHours(svg as SVGElement, bounds, sourceData, i18n, graphRange);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_HOURS_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_HOURS_SUBTITLE);
</script>

<div class="graph" id="graph-hour">
    <h1>{title}</h1>

    <div class="subtitle">{subtitle}</div>

    <div class="range-box-inner">
        <GraphRangeRadios bind:graphRange {i18n} {revlogRange} followRevlog={true} />
    </div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <path class="area" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>
</div>
