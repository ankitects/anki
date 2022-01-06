<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "../lib/ftl";
    import { Stats } from "../lib/proto";
    import { Timestamp, timeSpan } from "../lib/time";

    type StatsRevlogEntry = Stats.CardStatsResponse.StatsRevlogEntry;

    export let revlog: StatsRevlogEntry[];

    function reviewKindClass(entry: StatsRevlogEntry): string {
        switch (entry.reviewKind) {
            case Stats.RevlogEntry.ReviewKind.LEARNING:
                return "revlog-learn";
            case Stats.RevlogEntry.ReviewKind.REVIEW:
                return "revlog-review";
            case Stats.RevlogEntry.ReviewKind.RELEARNING:
                return "revlog-relearn";
        }
        return "";
    }

    function reviewKindLabel(entry: StatsRevlogEntry): string {
        switch (entry.reviewKind) {
            case Stats.RevlogEntry.ReviewKind.LEARNING:
                return tr2.cardStatsReviewLogTypeLearn();
            case Stats.RevlogEntry.ReviewKind.REVIEW:
                return tr2.cardStatsReviewLogTypeReview();
            case Stats.RevlogEntry.ReviewKind.RELEARNING:
                return tr2.cardStatsReviewLogTypeRelearn();
            case Stats.RevlogEntry.ReviewKind.FILTERED:
                return tr2.cardStatsReviewLogTypeFiltered();
            case Stats.RevlogEntry.ReviewKind.MANUAL:
                return tr2.cardStatsReviewLogTypeManual();
        }
    }

    function ratingClass(entry: StatsRevlogEntry): string {
        if (entry.buttonChosen === 1) {
            return "revlog-ease1";
        }
        return "";
    }

    interface RevlogRow {
        date: string;
        time: string;
        reviewKind: string;
        reviewKindClass: string;
        rating: number;
        ratingClass: string;
        interval: string;
        ease: string;
        takenSecs: string;
    }

    function revlogRowFromEntry(entry: StatsRevlogEntry): RevlogRow {
        const timestamp = new Timestamp(entry.time);

        return {
            date: timestamp.dateString(),
            time: timestamp.timeString(),
            reviewKind: reviewKindLabel(entry),
            reviewKindClass: reviewKindClass(entry),
            rating: entry.buttonChosen,
            ratingClass: ratingClass(entry),
            interval: timeSpan(entry.interval),
            ease: entry.ease ? `${entry.ease / 10}%` : "",
            takenSecs: timeSpan(entry.takenSecs, true),
        };
    }

    $: revlogRows = revlog.map(revlogRowFromEntry);
</script>

{#if revlog.length > 0}
    <table class="revlog-table">
        <tr>
            <th class="left">{tr2.cardStatsReviewLogDate()}</th>
            <th class="center hidden-xs">{tr2.cardStatsReviewLogType()}</th>
            <th class="center">{tr2.cardStatsReviewLogRating()}</th>
            <th class="center">{tr2.cardStatsInterval()}</th>
            <th class="center hidden-xs">{tr2.cardStatsEase()}</th>
            <th class="center">{tr2.cardStatsReviewLogTimeTaken()}</th>
        </tr>
        <tr>
            <td
                ><div class="column">
                    {#each revlogRows as row, _index}
                        <div><b>{row.date}</b> @ {row.time}</div>
                    {/each}
                </div></td
            >
            <td class="hidden-xs"
                ><div class="centered-cell">
                    <div class="column">
                        {#each revlogRows as row, _index}
                            <div class={row.reviewKindClass}>
                                {row.reviewKind}
                            </div>
                        {/each}
                    </div>
                </div></td
            >
            <td
                ><div class="centered-cell">
                    <div class="column">
                        {#each revlogRows as row, _index}
                            <div class={row.ratingClass}>{row.rating}</div>
                        {/each}
                    </div>
                </div></td
            >
            <td
                ><div class="centered-cell">
                    <div class="column column-right">
                        {#each revlogRows as row, _index}
                            <div>{row.interval}</div>
                        {/each}
                    </div>
                </div></td
            >
            <td class="hidden-xs"
                ><div class="centered-cell">
                    <div class="column">
                        {#each revlogRows as row, _index}
                            <div>{row.ease}</div>
                        {/each}
                    </div>
                </div></td
            >
            <td
                ><div class="centered-cell">
                    <div class="column column-right">
                        {#each revlogRows as row, _index}
                            <div>{row.takenSecs}</div>
                        {/each}
                    </div>
                </div></td
            >
        </tr>
    </table>
{/if}

<style>
    .column > div:empty::after {
        /* prevent collapsing of empty rows */
        content: "\00a0";
    }

    .left {
        text-align: start;
    }

    .center {
        text-align: center;
    }

    .revlog-table {
        width: 100%;
        border-spacing: 1em 0;
        border-collapse: collapse;
        white-space: nowrap;
    }

    .centered-cell {
        display: flex;
        flex-direction: row;
        justify-content: center;
        align-items: center;
    }

    .column {
        display: inline-flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
    }

    .column-right {
        align-items: flex-end;
    }

    .revlog-learn {
        color: var(--new-count);
    }

    .revlog-review {
        color: var(--review-count);
    }

    .revlog-relearn,
    .revlog-ease1 {
        color: var(--learn-count);
    }

    @media only screen and (max-device-width: 480px) and (orientation: portrait) {
        .hidden-xs {
            display: none;
        }
    }
</style>
