<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { GraphsResponse } from "@generated/anki/stats_pb";
    import * as tr2 from "@generated/ftl";
    import { createEventDispatcher } from "svelte";

    import type { GraphData, TableDatum } from "./card-counts";
    import { gatherData, renderCards } from "./card-counts";
    import Graph from "./Graph.svelte";
    import type { GraphPrefs } from "./graph-helpers";
    import type { SearchEventMap } from "./graph-helpers";
    import { defaultGraphBounds } from "./graph-helpers";
    import InputBox from "./InputBox.svelte";

    export let sourceData: GraphsResponse;
    export let prefs: GraphPrefs;

    const dispatch = createEventDispatcher<SearchEventMap>();

    let svg: HTMLElement | SVGElement | null = null;

    const bounds = defaultGraphBounds();
    bounds.width = 225;
    bounds.marginBottom = 0;

    let graphData: GraphData = null!;
    let tableData: TableDatum[] = null!;

    $: {
        graphData = gatherData(sourceData, $prefs.cardCountsSeparateInactive);
        tableData = renderCards(svg as any, bounds, graphData);
    }

    const label = tr2.statisticsCountsSeparateSuspendedBuriedCards();
    const total = tr2.statisticsCountsTotalCards();
</script>

<Graph title={graphData.title}>
    <InputBox>
        <label>
            <input type="checkbox" bind:checked={$prefs.cardCountsSeparateInactive} />
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
                <tbody>
                    {#each tableData as d, _idx}
                        <tr>
                            <!-- prettier-ignore -->
                            <td>
                            <span style="color: {d.colour};">■&nbsp;</span>
                            {#if $prefs.browserLinksSupported}
                                <button class="search-link" on:click={() => dispatch('search', { query: d.query })}>{d.label}</button>
                            {:else}
                                <span>{d.label}</span>
                            {/if}
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
                        <td></td>
                    </tr>
                </tbody>
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

    .search-link {
        border: none;
        background: transparent;
        cursor: pointer;
        box-shadow: none;
        padding: 1px 3px;
        margin-bottom: 0px;
    }

    .search-link:hover {
        color: var(--fg-link);
        text-decoration: underline;
    }
</style>
