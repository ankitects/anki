<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import { gatherData, GraphData, renderCards } from "./card-counts";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut;
    export let i18n: I18n;

    let svg = null as HTMLElement | SVGElement | null;

    let bounds = defaultGraphBounds();
    bounds.height = 20;
    bounds.marginLeft = 20;
    bounds.marginRight = 20;
    bounds.marginTop = 0;

    let graphData = (null as unknown) as GraphData;
    $: {
        graphData = gatherData(sourceData, i18n);
        renderCards(svg as any, bounds, graphData);
    }

    const total = i18n.tr(i18n.TR.STATISTICS_COUNTS_TOTAL_CARDS);
</script>

<style>
    svg {
        transition: opacity 1s;
    }
</style>

<div class="graph" id="graph-card-counts">
    <h1>{graphData.title}</h1>

    <svg
        bind:this={svg}
        viewBox={`0 0 ${bounds.width} ${bounds.height}`}
        style={{ opacity: graphData.totalCards ? 1 : 0 }}>
        <g class="days" />
    </svg>

    <div class="centered">{total}: {graphData.totalCards}</div>

</div>
