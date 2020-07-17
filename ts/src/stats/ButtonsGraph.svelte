<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import AxisTicks from "./AxisTicks.svelte";
    import { gatherData, GraphData, renderButtons } from "./buttons";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        renderButtons(svg as SVGElement, bounds, gatherData(sourceData), i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_SUBTITLE);
</script>

<div class="graph" id="graph-buttons">
    <h1>{title}</h1>

    <div class="subtitle">{subtitle}</div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>
</div>
