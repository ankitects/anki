<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { TagMasteryResponse_HowSure } from "@generated/anki/stats_pb";
    import type { TagMasteryResponse } from "@generated/anki/stats_pb";
    import { tagMastery } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { Timestamp } from "@tslib/time";

    import TitledContainer from "$lib/components/TitledContainer.svelte";

    // Group by AAMC section. MileDown is single-rooted under "MileDown::", so
    // the sections live at depth 2 (MileDown::Behavioral, MileDown::Biochemistry,
    // ...); depth 1 would collapse everything into one "MileDown" topic.
    const groupDepth = 2;

    let data: TagMasteryResponse | null = null;

    async function load(): Promise<void> {
        // masteredThreshold 0 -> backend default (echoed back as thresholdUsed).
        // Empty search -> whole collection.
        data = await tagMastery({ groupDepth, masteredThreshold: 0, search: "" });
    }

    load();

    const pct = (n: number): string => `${(n * 100).toFixed(1)}%`;

    function howSureLabel(howSure: TagMasteryResponse_HowSure): string {
        switch (howSure) {
            case TagMasteryResponse_HowSure.HIGH:
                return tr.statisticsMasteryHowSureHigh();
            case TagMasteryResponse_HowSure.MEDIUM:
                return tr.statisticsMasteryHowSureMedium();
            case TagMasteryResponse_HowSure.LOW:
                return tr.statisticsMasteryHowSureLow();
            case TagMasteryResponse_HowSure.INSUFFICIENT:
            default:
                return tr.statisticsMasteryHowSureInsufficient();
        }
    }

    // 0 is the backend's "no graded reviews yet" sentinel - never format it as a date.
    function lastUpdatedText(lastUpdatedSecs: bigint): string {
        const secs = Number(lastUpdatedSecs);
        if (secs === 0) {
            return tr.statisticsMasteryNoReviewsYet();
        }
        const timestamp = new Timestamp(secs);
        return tr.statisticsMasteryLastUpdated({
            when: `${timestamp.dateString()} ${timestamp.timeString()}`,
        });
    }
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

            <!--
                These fields are honest and computable independently of the
                give-up rule above (enoughData), so they render in both
                branches - showing them alongside "Not enough data yet" is
                intentional, not a contradiction.
            -->
            <!--
                Coverage / reasons / last-updated / next-topic are honest and
                computable independently of the give-up rule, so they render in
                both branches. "How sure" is the exception: it describes
                confidence in the readiness band, so it only shows once the band
                does (otherwise it would contradict "Not enough data yet").
            -->
            <div class="mastery-summary">
                <div class="coverage">
                    <div class="coverage-label">
                        {tr.statisticsMasteryCoverage({
                            selected: data.topicsCovered,
                            total: data.topicsTotal,
                        })}
                    </div>
                    <div
                        class="progress"
                        style="--v: {data.topicsTotal
                            ? data.topicsCovered / data.topicsTotal
                            : 0}"
                        aria-hidden="true"
                    >
                        <div class="fill"></div>
                    </div>
                </div>

                {#if data.enoughData && data.overallN > 0}
                    <div class="how-sure">
                        {tr.statisticsMasteryHowSure({ level: howSureLabel(data.howSure) })}
                    </div>
                {/if}

                <div class="meta">
                    <span
                        >{tr.statisticsMasteryReasons({
                            reviews: data.totalGradedReviews,
                            count: data.topicsWithReviews,
                            total: data.topicsTotal,
                        })}</span
                    >
                    <span>{lastUpdatedText(data.lastUpdatedSecs)}</span>
                </div>

                {#if data.nextTopic !== ""}
                    <div class="next-topic">
                        {tr.statisticsMasteryNextTopic({ topic: data.nextTopic })}
                    </div>
                {/if}
            </div>
        </TitledContainer>

        <TitledContainer title={tr.statisticsMasteryTitle()}>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th scope="col" class="left">{tr.statisticsMasteryTopic()}</th>
                            <th scope="col">{tr.statisticsMasteryCards()}</th>
                            <th scope="col">{tr.statisticsMasteryScored()}</th>
                            <th scope="col">{tr.statisticsMasteryMastered()}</th>
                            <th scope="col" class="recall-col"
                                >{tr.statisticsMasteryAverageRecall()}</th
                            >
                            <th scope="col">{tr.statisticsMasteryReviews()}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each data.groups as group (group.tag)}
                            <tr class:untouched={group.cardsWithState === 0}>
                                <td class="left">{group.tag}</td>
                                <td>{group.totalCards}</td>
                                <td>{group.cardsWithState}</td>
                                <td>{group.masteredCards}</td>
                                <td class="recall-col">
                                    {#if group.cardsWithState > 0}
                                        <div
                                            class="recall-bar"
                                            style="--v: {group.averageRecall}"
                                        >
                                            <div class="fill" aria-hidden="true"></div>
                                            <span class="val">{pct(group.averageRecall)}</span>
                                        </div>
                                    {:else}
                                        <span class="dash">–</span>
                                    {/if}
                                </td>
                                <td>{group.gradedReviews}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
            <div class="caption">
                {tr.statisticsMasteryCutoff({
                    percent: Math.round(data.thresholdUsed * 100),
                })}
            </div>
        </TitledContainer>
    {/if}
</div>

<style lang="scss">
    // Neutral fills only — never a red/yellow/green scale. Colour must not
    // imply "green = ready", per the honesty rule (recall != readiness).
    $track: color-mix(in srgb, var(--fg, #000) 10%, transparent);
    $fill: color-mix(in srgb, var(--fg, #000) 45%, transparent);
    $bar: color-mix(in srgb, var(--fg, #000) 16%, transparent);
    $hover: color-mix(in srgb, var(--fg, #000) 5%, transparent);
    $chip: color-mix(in srgb, var(--fg, #000) 8%, transparent);

    .mastery-page {
        max-width: 60em;
        margin: 0 auto;
        padding: 1em;
        // Line up digits in the data columns so rows scan cleanly.
        font-variant-numeric: tabular-nums;
    }

    .readiness {
        text-align: center;
        padding: 0.25em 0 0.5em;

        .point {
            font-size: 2.6em;
            font-weight: 700;
            line-height: 1.1;
        }

        .range,
        .basis {
            color: var(--fg-subtle, #666);
            font-size: 0.9em;
        }

        &.empty {
            color: var(--fg-subtle, #666);
            font-style: italic;
            padding: 0.75em 0;
        }
    }

    .mastery-summary {
        display: flex;
        flex-direction: column;
        gap: 0.6em;
        margin-top: 0.85em;
        padding-top: 0.85em;
        border-top: 1px solid var(--border, #8884);
        font-size: 0.9em;
    }

    .coverage {
        display: flex;
        flex-direction: column;
        gap: 0.3em;

        .coverage-label {
            font-weight: 600;
        }

        .progress {
            height: 6px;
            border-radius: 999px;
            background: $track;
            overflow: hidden;

            .fill {
                height: 100%;
                width: calc(var(--v, 0) * 100%);
                background: $fill;
                border-radius: inherit;
                transition: width 250ms ease-out;
            }
        }
    }

    .how-sure {
        font-weight: 600;
    }

    .meta {
        display: flex;
        flex-wrap: wrap;
        gap: 0.2em 1.2em;
        color: var(--fg-subtle, #666);
    }

    .next-topic {
        align-self: flex-start;
        font-weight: 600;
        padding: 0.35em 0.7em;
        border-radius: 8px;
        background: $chip;
        border: 1px solid var(--border, #8884);
    }

    .table-wrap {
        overflow-x: auto;
    }

    table {
        width: 100%;
        border-collapse: collapse;
        font-variant-numeric: tabular-nums;

        th,
        td {
            padding: 6px 10px;
            text-align: right;
            white-space: nowrap;
        }

        th {
            border-bottom: 1px solid var(--border, #8884);
            font-weight: 600;
            font-size: 0.85em;
            color: var(--fg-subtle, #555);
        }

        .left {
            text-align: start;
        }

        td.left {
            font-weight: 500;
        }

        tbody tr:hover {
            background: $hover;
        }

        // De-emphasise topics with no scored cards so studied ones stand out.
        tbody tr.untouched td {
            color: var(--fg-subtle, #999);
        }

        .dash {
            color: var(--fg-subtle, #999);
        }
    }

    .recall-col {
        min-width: 6.5em;
    }

    // Subtle in-cell bar behind the always-visible % — magnitude at a glance,
    // value never hidden behind colour.
    .recall-bar {
        position: relative;
        display: flex;
        justify-content: flex-end;
        align-items: center;

        .fill {
            position: absolute;
            left: 0;
            top: 2px;
            bottom: 2px;
            width: calc(var(--v, 0) * 100%);
            background: $bar;
            border-radius: 4px;
        }

        .val {
            position: relative;
        }
    }

    .caption {
        margin-top: 0.6em;
        color: var(--fg-subtle, #666);
        font-size: 0.85em;
        text-align: center;
    }

    @media (prefers-reduced-motion: reduce) {
        .coverage .progress .fill {
            transition: none;
        }
    }
</style>
