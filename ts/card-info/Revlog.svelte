<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "../lib/ftl";
    import { Stats } from "../lib/proto";
    import { Timestamp, timeSpan } from "../lib/time";

    export let stats: Stats.CardStatsResponse;

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

    let revlogRows: RevlogRow[];
    $: revlogRows = stats.revlog.map((entry) => revlogRowFromEntry(entry));
</script>

{#if stats.revlog.length}
    <div class="revlog-container">
        <table class="revlog-table">
            <tr>
                <th class="left">{tr2.cardStatsReviewLogDate()}</th>
                <th class="hidden-xs">{tr2.cardStatsReviewLogType()}</th>
                <th>{tr2.cardStatsReviewLogRating()}</th>
                <th>{tr2.cardStatsInterval()}</th>
                <th class="hidden-xs">{tr2.cardStatsEase()}</th>
                <th>{tr2.cardStatsReviewLogTimeTaken()}</th>
            </tr>
            {#each revlogRows as row, _index}
                <tr>
                    <td class="left"><b>{row.date}</b> @ {row.time}</td>
                    <td class="center hidden-xs {row.reviewKindClass}">
                        {row.reviewKind}
                    </td>
                    <td class="center {row.ratingClass}">{row.rating}</td>
                    <td class="center">{row.interval}</td>
                    <td class="center hidden-xs">{row.ease}</td>
                    <td class="right">{row.takenSecs}</td>
                </tr>
            {/each}
        </table>
    </div>
{/if}

<style>
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
        margin-top: 2em;
    }

    .revlog-table {
        width: 100%;
        border-spacing: 1em 0;
        border-collapse: collapse;
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
