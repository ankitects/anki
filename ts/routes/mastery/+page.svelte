<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { TagMasteryResponse } from "@generated/anki/stats_pb";
    import { tagMastery } from "@generated/backend";
    import * as tr from "@generated/ftl";

    import TitledContainer from "$lib/components/TitledContainer.svelte";

    // Group by the top-level tag (maps to AAMC sections); 0 would keep whole tags.
    const groupDepth = 1;

    let data: TagMasteryResponse | null = null;

    async function load(): Promise<void> {
        // masteredThreshold 0 -> backend default (echoed back as thresholdUsed).
        // Empty search -> whole collection.
        data = await tagMastery({ groupDepth, masteredThreshold: 0, search: "" });
    }

    load();

    const pct = (n: number): string => `${(n * 100).toFixed(1)}%`;
</script>

<div class="mastery-page">
    {#if data}
        <TitledContainer title={tr.statisticsMasteryReadiness()}>
            {#if !data.enoughData || data.overallN === 0}
                <div class="readiness empty">{tr.statisticsMasteryNotEnoughData()}</div>
            {:else}
                <div class="readiness">
                    <div class="point">{pct(data.overallMeanRecall)}</div>
                    <div class="range">
                        {tr.statisticsMasteryLikelyRange()}: {pct(data.overallCiLow)} – {pct(
                            data.overallCiHigh,
                        )}
                    </div>
                    <div class="basis">
                        {tr.statisticsMasteryBasedOn({ cards: data.overallN })}
                    </div>
                </div>
            {/if}
        </TitledContainer>

        <TitledContainer title={tr.statisticsMasteryTitle()}>
            <table>
                <thead>
                    <tr>
                        <th class="left">{tr.statisticsMasteryTopic()}</th>
                        <th>{tr.statisticsMasteryCards()}</th>
                        <th>{tr.statisticsMasteryScored()}</th>
                        <th>{tr.statisticsMasteryMastered()}</th>
                        <th>{tr.statisticsMasteryAverageRecall()}</th>
                        <th>{tr.statisticsMasteryReviews()}</th>
                    </tr>
                </thead>
                <tbody>
                    {#each data.groups as group (group.tag)}
                        <tr>
                            <td class="left">{group.tag}</td>
                            <td>{group.totalCards}</td>
                            <td>{group.cardsWithState}</td>
                            <td>{group.masteredCards}</td>
                            <td>
                                {group.cardsWithState > 0
                                    ? pct(group.averageRecall)
                                    : "–"}
                            </td>
                            <td>{group.gradedReviews}</td>
                        </tr>
                    {/each}
                </tbody>
            </table>
            <div class="caption">
                {tr.statisticsMasteryCutoff({
                    percent: Math.round(data.thresholdUsed * 100),
                })}
            </div>
        </TitledContainer>
    {/if}
</div>

<style lang="scss">
    .mastery-page {
        max-width: 60em;
        margin: 0 auto;
        padding: 1em;
    }

    .readiness {
        text-align: center;
        padding: 0.5em 0;

        .point {
            font-size: 2.5em;
            font-weight: bold;
        }

        .range,
        .basis {
            opacity: 0.7;
        }

        &.empty {
            opacity: 0.6;
            font-style: italic;
        }
    }

    table {
        width: 100%;
        border-collapse: collapse;

        th,
        td {
            padding: 4px 8px;
            text-align: right;
        }

        th {
            border-bottom: 1px solid var(--border, #8884);
        }

        .left {
            text-align: start;
        }

        tbody tr:nth-child(even) {
            background: var(--canvas-elevated, #8881);
        }
    }

    .caption {
        margin-top: 0.5em;
        opacity: 0.6;
        font-size: 0.85em;
        text-align: center;
    }
</style>
