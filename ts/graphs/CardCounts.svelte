<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";
    import { createEventDispatcher } from "svelte";

    import type { PreferenceStore } from "../sveltelib/preferences";
    import type { GraphData, TableDatum } from "./card-counts";
    import { gatherData, renderCards } from "./card-counts";
    import Graph from "./Graph.svelte";
    import type { SearchEventMap } from "./graph-helpers";
    import { defaultGraphBounds } from "./graph-helpers";
    import InputBox from "./InputBox.svelte";

    export let sourceData: Stats.GraphsResponse;
    export let preferences: PreferenceStore<Stats.GraphPreferences>;

    const { cardCountsSeparateInactive, browserLinksSupported } = preferences;
    const dispatch = createEventDispatcher<SearchEventMap>();

    let svg = null as HTMLElement | SVGElement | null;

    const bounds = defaultGraphBounds();
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
        <div class="svg-container">
            <svg
                bind:this={svg}
                viewBox={`0 0 ${bounds.width} ${bounds.height}`}
                style="opacity: {graphData.totalCards ? 1 : 0}"
            >
                <g class="counts" />
            </svg>
        </div>
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
        transition: opacity var(--transition-slow);
    }

    .counts-outer {
        display: flex;
        justify-content: center;
        margin: 0 4vw;
        flex-wrap: wrap;
        flex: 1;

        .svg-container {
            width: 225px;
        }

        .counts-table {
            display: flex;
            flex-direction: column;
            justify-content: center;

            table {
                border-spacing: 1em 0;
                padding-left: 4vw;

                td {
                    white-space: nowrap;
                    padding: 0 min(4vw, 40px);

                    &.right {
                        text-align: right;
                    }
                }
            }
        }
    }

    .search-link:hover {
        cursor: pointer;
        color: var(--fg-link);
        text-decoration: underline;
    }
</style>
