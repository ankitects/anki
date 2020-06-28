<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import AxisTicks from "./AxisTicks.svelte";
    import AxisLabels from "./AxisLabels.svelte";
    import { gatherData, GraphData, renderButtons } from "./buttons";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    const bounds = defaultGraphBounds();
    const xText = "";
    const yText = "Times pressed";

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        console.log("gathering data");
        renderButtons(svg as SVGElement, bounds, gatherData(sourceData));
    }

    const title = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_TITLE);
</script>

<div class="graph">
    <h1>{title}</h1>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <AxisLabels {bounds} {xText} {yText} />
    </svg>
</div>
