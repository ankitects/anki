<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "@tslib/ftl";
    import { Stats } from "@tslib/proto";
    import { timeSpan, Timestamp } from "@tslib/time";

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
    <div class="revlog-table">
        <div class="column">
            <div class="column-head">{tr2.cardStatsReviewLogDate()}</div>
            <div class="column-content">
                {#each revlogRows as row, _index}
                    <div>
                        <b>{row.date}</b>
                        <span class="hidden-xs">@ {row.time}</span>
                    </div>
                {/each}
            </div>
        </div>
        <div class="column hidden-xs">
            <div class="column-head">{tr2.cardStatsReviewLogType()}</div>
            <div class="column-content">
                {#each revlogRows as row, _index}
                    <div class={row.reviewKindClass}>
                        {row.reviewKind}
                    </div>
                {/each}
            </div>
        </div>
        <div class="column">
            <div class="column-head">{tr2.cardStatsReviewLogRating()}</div>
            <div class="column-content">
                {#each revlogRows as row, _index}
                    <div class={row.ratingClass}>{row.rating}</div>
                {/each}
            </div>
        </div>
        <div class="column">
            <div class="column-head">{tr2.cardStatsInterval()}</div>
            <div class="column-content right">
                {#each revlogRows as row, _index}
                    <div>{row.interval}</div>
                {/each}
            </div>
        </div>
        <div class="column hidden-xs">
            <div class="column-head">{tr2.cardStatsEase()}</div>
            <div class="column-content">
                {#each revlogRows as row, _index}
                    <div>{row.ease}</div>
                {/each}
            </div>
        </div>
        <div class="column">
            <div class="column-head">{tr2.cardStatsReviewLogTimeTaken()}</div>
            <div class="column-content right">
                {#each revlogRows as row, _index}
                    <div>{row.takenSecs}</div>
                {/each}
            </div>
        </div>
    </div>
{/if}

<style lang="scss">
    .revlog-table {
        width: 100%;
        max-width: 50em;
        display: flex;
        flex-direction: row;
        justify-content: space-between;
        align-items: center;
        white-space: nowrap;
    }

    .column {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;

        &:not(:last-child) {
            margin-right: 0.5em;
        }
    }

    .column-head {
        font-weight: bold;
    }

    .column-content {
        display: inline-flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;

        &.right {
            align-items: flex-end;
        }

        > div:empty::after {
            /* prevent collapsing of empty rows */
            content: "\00a0";
        }
    }

    .revlog-learn {
        color: var(--state-new);
    }

    .revlog-review {
        color: var(--state-review);
    }

    .revlog-relearn,
    .revlog-ease1 {
        color: var(--state-learn);
    }

    @media only screen and (max-device-width: 480px) and (orientation: portrait) {
        .hidden-xs {
            display: none;
        }
    }
</style>
