<script lang="typescript">
    import { defaultGraphBounds } from "./graphs";
    import { gatherData, GraphData, renderCards, TableDatum } from "./card-counts";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut;
    export let i18n: I18n;

    let svg = null as HTMLElement | SVGElement | null;

    let bounds = defaultGraphBounds();
    bounds.width = 225;
    bounds.marginBottom = 0;

    let graphData = (null as unknown) as GraphData;
    let tableData = (null as unknown) as TableDatum[];
    $: {
        graphData = gatherData(sourceData, i18n);
        tableData = renderCards(svg as any, bounds, graphData);
    }

    const total = i18n.tr(i18n.TR.STATISTICS_COUNTS_TOTAL_CARDS);
</script>

<style>
    svg {
        transition: opacity 1s;
    }

    .counts-outer {
        display: flex;
        justify-content: center;
    }

    .counts-table {
        display: flex;
        flex-direction: column;
        justify-content: center;
    }

    table {
        border-spacing: 1em 0;
    }

    .right {
        text-align: right;
    }
</style>

<div class="graph" id="graph-card-counts">
    <h1>{graphData.title}</h1>

    <div class="counts-outer">
        <svg
            bind:this={svg}
            viewBox={`0 0 ${bounds.width} ${bounds.height}`}
            width={bounds.width}
            height={bounds.height}
            style="opacity: {graphData.totalCards ? 1 : 0}">
            <g class="counts" />
        </svg>
        <div class="counts-table">
            <table>
                {#each tableData as d, idx}
                    <tr>
                        <!-- prettier-ignore -->
                        <td>
                            <span style="color: {d.colour};">■&nbsp;</span>{d.label}
                        </td>
                        <td class="right">{d.count}</td>
                        <td class="right">{d.percent}</td>
                    </tr>
                {/each}

                <tr>
                    <td>
                        <span style="visibility: hidden;">■</span>
                        {total}
                    </td>
                    <td class="right">{graphData.totalCards}</td>
                    <td />
                </tr>
            </table>
        </div>
    </div>
</div>
