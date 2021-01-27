<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import { defaultGraphBounds } from "./graph-helpers";
    import type { SearchEventMap } from "./graph-helpers";
    import { gatherData, renderCards } from "./card-counts";
    import type { GraphData, TableDatum } from "./card-counts";
    import type { PreferenceStore } from "./preferences";
    import type pb from "anki/backend_proto";
    import type { I18n } from "anki/i18n";

    export let sourceData: pb.BackendProto.GraphsOut;
    export let i18n: I18n;
    export let preferences: PreferenceStore;

    let { cardCountsSeparateInactive, browserLinksSupported } = preferences;
    const dispatch = createEventDispatcher<SearchEventMap>();

    let svg = null as HTMLElement | SVGElement | null;

    let bounds = defaultGraphBounds();
    bounds.width = 225;
    bounds.marginBottom = 0;

    let graphData = (null as unknown) as GraphData;
    let tableData = (null as unknown) as TableDatum[];

    $: {
        graphData = gatherData(sourceData, $cardCountsSeparateInactive, i18n);
        tableData = renderCards(svg as any, bounds, graphData);
    }

    const label = i18n.tr(i18n.TR.STATISTICS_COUNTS_SEPARATE_SUSPENDED_BURIED_CARDS);
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

    .search-link:hover {
        cursor: pointer;
        color: var(--link);
        text-decoration: underline;
    }
</style>

<div class="graph" id="graph-card-counts">
    <h1>{graphData.title}</h1>

    <div class="range-box-inner">
        <label>
            <input type="checkbox" bind:checked={$cardCountsSeparateInactive} />
            {label}
        </label>
    </div>

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
                {#each tableData as d, _idx}
                    <tr>
                        <!-- prettier-ignore -->
                        <td>
                            <span style="color: {d.colour};">■&nbsp;</span>
                            {#if browserLinksSupported}
                                <span class="search-link" on:click={() => dispatch('search', { query: d.query })}>{d.label}</span>
                            {:else}
                                <span>{d.label}</span>
                            {/if}
                        </td>
                        <td class="right">{d.count}</td>
                        <td class="right">{d.percent}</td>
                    </tr>
                {/each}

                <tr>
                    <td><span style="visibility: hidden;">■</span> {total}</td>
                    <td class="right">{graphData.totalCards}</td>
                    <td />
                </tr>
            </table>
        </div>
    </div>
</div>
