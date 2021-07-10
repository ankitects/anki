<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import type { Stats } from "lib/proto";
    import type { PreferenceStore } from "sveltelib/preferences";

    import Graph from "./Graph.svelte";
    import InputBox from "./InputBox.svelte";

    import { defaultGraphBounds } from "./graph-helpers";
    import type { SearchEventMap } from "./graph-helpers";
    import { gatherData, renderCards } from "./card-counts";
    import type { GraphData, TableDatum } from "./card-counts";

    export let sourceData: Stats.GraphsResponse;
    import * as tr2 from "lib/i18n";
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    let { cardCountsSeparateInactive, browserLinksSupported } = preferences;
    const dispatch = createEventDispatcher<SearchEventMap>();

    let svg = null as HTMLElement | SVGElement | null;

    let bounds = defaultGraphBounds();
    bounds.width = 225;
    bounds.marginBottom = 0;

    let graphData = null as unknown as GraphData;
    let tableData = null as unknown as TableDatum[];

    $: {
        graphData = gatherData(sourceData, $cardCountsSeparateInactive);
        tableData = renderCards(svg as any, bounds, graphData);
    }

    const label = tr2.statisticsCountsSeparateSuspendedBuriedCards();
    const total = tr2.statisticsCountsTotalCards();
</script>

<Graph title={graphData.title}>
    <InputBox>
        <label>
            <input type="checkbox" bind:checked={$cardCountsSeparateInactive} />
            {label}
        </label>
    </InputBox>

    <div class="counts-outer">
        <svg
            bind:this={svg}
            viewBox={`0 0 ${bounds.width} ${bounds.height}`}
            width={bounds.width}
            height={bounds.height}
            style="opacity: {graphData.totalCards ? 1 : 0}"
        >
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
</Graph>

<style lang="scss">
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

        td {
            white-space: nowrap;
        }
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
