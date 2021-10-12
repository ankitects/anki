<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "../lib/ftl";
    import { Stats, unwrapOptionalNumber } from "../lib/proto";
    import { Timestamp, timeSpan, DAY } from "../lib/time";

    export let stats: Stats.CardStatsResponse;

    function dateString(timestamp: number): string {
        return new Timestamp(timestamp).dateString();
    }

    class StatsRow {
        constructor(public label: string, public value: string | number) {}
    }

    const statsRows: StatsRow[] = [];

    statsRows.push(new StatsRow(tr2.cardStatsAdded(), dateString(stats.added)));

    const firstReview = unwrapOptionalNumber(stats.firstReview);
    if (firstReview) {
        statsRows.push(
            new StatsRow(tr2.cardStatsFirstReview(), dateString(firstReview))
        );
    }
    const latestReview = unwrapOptionalNumber(stats.latestReview);
    if (latestReview) {
        statsRows.push(
            new StatsRow(tr2.cardStatsLatestReview(), dateString(latestReview))
        );
    }

    const dueDate = unwrapOptionalNumber(stats.dueDate);
    if (dueDate) {
        statsRows.push(new StatsRow(tr2.statisticsDueDate(), dateString(dueDate)));
    }
    const duePosition = unwrapOptionalNumber(stats.duePosition);
    if (duePosition) {
        statsRows.push(
            new StatsRow(tr2.cardStatsNewCardPosition(), dateString(duePosition))
        );
    }

    if (stats.interval) {
        statsRows.push(
            new StatsRow(tr2.cardStatsInterval(), timeSpan(stats.interval * DAY))
        );
    }
    if (stats.ease) {
        statsRows.push(new StatsRow(tr2.cardStatsEase(), `${stats.ease / 10}%`));
    }

    statsRows.push(new StatsRow(tr2.cardStatsReviewCount(), stats.reviews));
    statsRows.push(new StatsRow(tr2.cardStatsLapseCount(), stats.lapses));

    if (stats.totalSecs) {
        statsRows.push(
            new StatsRow(tr2.cardStatsAverageTime(), timeSpan(stats.averageSecs))
        );
        statsRows.push(
            new StatsRow(tr2.cardStatsTotalTime(), timeSpan(stats.totalSecs))
        );
    }

    statsRows.push(new StatsRow(tr2.cardStatsCardTemplate(), stats.cardType));
    statsRows.push(new StatsRow(tr2.cardStatsNoteType(), stats.notetype));
    statsRows.push(new StatsRow(tr2.cardStatsDeckName(), stats.deck));

    statsRows.push(new StatsRow(tr2.cardStatsCardId(), stats.cardId));
    statsRows.push(new StatsRow(tr2.cardStatsNoteId(), stats.noteId));

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

    class RevlogRow {
        public date: string;
        public time: string;
        public reviewKind: string;
        public reviewKindClass: string;
        public rating: number;
        public ratingClass: string;
        public interval: string;
        public ease: string;
        public takenSecs: string;
        constructor(entry: IStatsRevlogEntry) {
            const timestamp = new Timestamp(entry.time!);
            this.date = timestamp.dateString();
            this.time = timestamp.timeString();
            this.reviewKind = reviewKindLabel(entry);
            this.reviewKindClass = reviewKindClass(entry);
            this.rating = entry.buttonChosen!;
            this.ratingClass = ratingClass(entry);
            this.interval = timeSpan(entry.interval!);
            this.ease = entry.ease ? `${entry.ease / 10}%` : "";
            this.takenSecs = timeSpan(entry.takenSecs!, true);
        }
    }

    const revlogRows: RevlogRow[] = stats.revlog.map((entry) => new RevlogRow(entry));
</script>

<div class="container">
    <div>
        <table class="stats-table">
            {#each statsRows as row, _index}
                <tr>
                    <th style="text-align:start">{row.label}</th>
                    <td>{row.value}</td>
                </tr>
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
                    {#each revlogRows as row, _index}
                        <tr>
                            <td class="left"><b>{row.date}</b> @ {row.time}</td>
                            <td class="center {row.reviewKindClass}">
                                {row.reviewKind}
                            </td>
                            <td class="center {row.ratingClass}">{row.rating}</td>
                            <td class="center">{row.interval}</td>
                            <td class="center">{row.ease}</td>
                            <td class="right">{row.takenSecs}</td>
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
        text-align: start;
    }

    .left {
        text-align: start;
    }

    .right {
        text-align: end;
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
