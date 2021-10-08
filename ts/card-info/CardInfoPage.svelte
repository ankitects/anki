<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { Stats } from "../lib/proto";

    export let stats: Stats.CardStatsResponse;
    import * as tr2 from "../lib/i18n";

    class StatsRow {
        constructor(public label: string, public value: string) {}
    }

    let statsRows: StatsRow[] = [
        new StatsRow(tr2.cardStatsAdded(), stats.added),
        new StatsRow(tr2.cardStatsFirstReview(), stats.firstReview),
        new StatsRow(tr2.cardStatsLatestReview(), stats.latestReview),
        new StatsRow(tr2.statisticsDueDate(), stats.dueDate),
        new StatsRow(tr2.cardStatsNewCardPosition(), stats.duePosition),
        new StatsRow(tr2.cardStatsInterval(), stats.interval),
        new StatsRow(tr2.cardStatsEase(), stats.ease),
        new StatsRow(tr2.cardStatsReviewCount(), stats.reviews),
        new StatsRow(tr2.cardStatsLapseCount(), stats.lapses),
        new StatsRow(tr2.cardStatsAverageTime(), stats.averageSecs),
        new StatsRow(tr2.cardStatsTotalTime(), stats.totalSecs),
        new StatsRow(tr2.cardStatsCardTemplate(), stats.cardType),
        new StatsRow(tr2.cardStatsNoteType(), stats.notetype),
        new StatsRow(tr2.cardStatsDeckName(), stats.deck),
        new StatsRow(tr2.cardStatsCardId(), stats.cardId),
        new StatsRow(tr2.cardStatsNoteId(), stats.noteId),
    ];

    type IStatsRevlogEntry = Stats.CardStatsResponse.IStatsRevlogEntry;
    function reviewKindClass(entry: IStatsRevlogEntry): string {
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

    function reviewKindLabel(entry: IStatsRevlogEntry): string {
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

    function ratingClass(entry: IStatsRevlogEntry): string {
        if (entry.buttonChosen === 1) {
            return "revlog-ease1";
        }
        return "";
    }
</script>

<div class="container">
    <div>
        <table class="stats-table">
            {#each statsRows as row, _index}
                {#if row.value}
                    <tr>
                        <th>{row.label}</th>
                        <td>{row.value}</td>
                    </tr>
                {/if}
            {/each}
        </table>
        {#if stats.revlog.length}
            <div class="revlog-container">
                <table class="revlog-table">
                    <tr>
                        <th>{tr2.cardStatsReviewLogDate()}</th>
                        <th>{tr2.cardStatsReviewLogType()}</th>
                        <th>{tr2.cardStatsReviewLogRating()}</th>
                        <th>{tr2.cardStatsInterval()}</th>
                        <th>{tr2.cardStatsEase()}</th>
                        <th>{tr2.cardStatsReviewLogTimeTaken()}</th>
                    </tr>
                    {#each stats.revlog as entry, _index}
                        <tr>
                            <td class="left">{entry.time}</td>
                            <td class="center {reviewKindClass(entry)}">
                                {reviewKindLabel(entry)}
                            </td>
                            <td class="center {ratingClass(entry)}"
                                >{entry.buttonChosen}</td
                            >
                            <td class="center">{entry.interval}</td>
                            <td class="center">{entry.ease}</td>
                            <td class="right">{entry.takenSecs}</td>
                        </tr>
                    {/each}
                </table>
            </div>
        {/if}
    </div>
</div>

<style>
    .container {
        display: flex;
        justify-content: center;
        white-space: nowrap;
    }

    .stats-table {
        width: 100%;
        text-align: left;
    }

    .left {
        text-align: left;
    }

    .right {
        text-align: right;
    }

    .center {
        text-align: center;
    }

    .revlog-container {
        margin: 4em -2em 0 -2em;
    }

    .revlog-table {
        width: 100%;
        border-spacing: 2em 0em;
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
</style>
