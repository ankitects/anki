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

    interface StatsRow {
        label: string;
        value: string | number;
    }

    const statsRows: StatsRow[] = [];

    statsRows.push({ label: tr2.cardStatsAdded(), value: dateString(stats.added) });

    const firstReview = unwrapOptionalNumber(stats.firstReview);
    if (firstReview) {
        statsRows.push({
            label: tr2.cardStatsFirstReview(),
            value: dateString(firstReview),
        });
    }
    const latestReview = unwrapOptionalNumber(stats.latestReview);
    if (latestReview) {
        statsRows.push({
            label: tr2.cardStatsLatestReview(),
            value: dateString(latestReview),
        });
    }

    const dueDate = unwrapOptionalNumber(stats.dueDate);
    if (dueDate) {
        statsRows.push({ label: tr2.statisticsDueDate(), value: dateString(dueDate) });
    }
    const duePosition = unwrapOptionalNumber(stats.duePosition);
    if (duePosition) {
        statsRows.push({
            label: tr2.cardStatsNewCardPosition(),
            value: dateString(duePosition),
        });
    }

    if (stats.interval) {
        statsRows.push({
            label: tr2.cardStatsInterval(),
            value: timeSpan(stats.interval * DAY),
        });
    }
    if (stats.ease) {
        statsRows.push({ label: tr2.cardStatsEase(), value: `${stats.ease / 10}%` });
    }

    statsRows.push({ label: tr2.cardStatsReviewCount(), value: stats.reviews });
    statsRows.push({ label: tr2.cardStatsLapseCount(), value: stats.lapses });

    if (stats.totalSecs) {
        statsRows.push({
            label: tr2.cardStatsAverageTime(),
            value: timeSpan(stats.averageSecs),
        });
        statsRows.push({
            label: tr2.cardStatsTotalTime(),
            value: timeSpan(stats.totalSecs),
        });
    }

    statsRows.push({ label: tr2.cardStatsCardTemplate(), value: stats.cardType });
    statsRows.push({ label: tr2.cardStatsNoteType(), value: stats.notetype });
    statsRows.push({ label: tr2.cardStatsDeckName(), value: stats.deck });

    statsRows.push({ label: tr2.cardStatsCardId(), value: stats.cardId });
    statsRows.push({ label: tr2.cardStatsNoteId(), value: stats.noteId });

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

    function revlogRowFromEntry(entry: IStatsRevlogEntry): RevlogRow {
        const timestamp = new Timestamp(entry.time!);
        return {
            date: timestamp.dateString(),
            time: timestamp.timeString(),
            reviewKind: reviewKindLabel(entry),
            reviewKindClass: reviewKindClass(entry),
            rating: entry.buttonChosen!,
            ratingClass: ratingClass(entry),
            interval: timeSpan(entry.interval!),
            ease: entry.ease ? `${entry.ease / 10}%` : "",
            takenSecs: timeSpan(entry.takenSecs!, true),
        };
    }

    const revlogRows: RevlogRow[] = stats.revlog.map((entry) =>
        revlogRowFromEntry(entry)
    );
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
