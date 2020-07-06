<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import AxisTicks from "./AxisTicks.svelte";
    import { gatherData, GraphData, renderHours } from "./hours";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";
    import NoDataOverlay from "./NoDataOverlay.svelte";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    const bounds = defaultGraphBounds();

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        renderHours(svg as SVGElement, bounds, gatherData(sourceData), i18n);
    }

    const title = i18n.tr(i18n.TR.STATISTICS_HOURS_TITLE);
    const subtitle = i18n.tr(i18n.TR.STATISTICS_HOURS_SUBTITLE);
</script>

<div class="graph">
    <h1>{title}</h1>

    <div class="subtitle">{subtitle}</div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <path class="area" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>
</div>
