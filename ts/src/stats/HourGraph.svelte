<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import AxisTicks from "./AxisTicks.svelte";
    import AxisLabels from "./AxisLabels.svelte";
    import { gatherData, GraphData, renderHours } from "./hours";
    import pb from "../backend/proto";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    const bounds = defaultGraphBounds();
    const xText = "";
    const yText = "Times pressed";

    let svg = null as HTMLElement | SVGElement | null;

    $: if (sourceData) {
        console.log("gathering data");
        renderHours(svg as SVGElement, bounds, gatherData(sourceData));
    }
</script>

<div class="graph">
    <h1>Hours</h1>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="bars" />
        <path class="area" />
        <g class="hoverzone" />
        <AxisTicks {bounds} />
        <AxisLabels {bounds} {xText} {yText} />
    </svg>
</div>
