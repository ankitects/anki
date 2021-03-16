<script lang="typescript">
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import GraphRangeRadios from "./GraphRangeRadios.svelte";

    import { renderButtons } from "./buttons";
    import { defaultGraphBounds, GraphRange, RevlogRange } from "./graph-helpers";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;
    export let revlogRange: RevlogRange;

    let graphRange: GraphRange = GraphRange.Year;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        renderButtons(svg as SVGElement, bounds, sourceData, i18n, graphRange);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_SUBTITLE);
</script>

<Graph {title} {subtitle}>
    <InputBox>
        <GraphRangeRadios bind:graphRange {i18n} {revlogRange} followRevlog={true} />
    </InputBox>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>
</Graph>
