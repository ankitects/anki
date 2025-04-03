<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { CardStatsResponse_StatsRevlogEntry as RevlogEntry } from "@generated/anki/stats_pb";
    import { RevlogEntry_ReviewKind as ReviewKind } from "@generated/anki/stats_pb";
    import * as tr2 from "@generated/ftl";
    import { timeSpan, Timestamp } from "@tslib/time";
    import { filterRevlogEntryByReviewKind } from "./forgetting-curve";

    export let revlog: RevlogEntry[];
    export let fsrsEnabled: boolean = false;

    function reviewKindClass(entry: RevlogEntry): string {
        switch (entry.reviewKind) {
            case ReviewKind.LEARNING:
                return "revlog-learn";
            case ReviewKind.REVIEW:
                return "revlog-review";
            case ReviewKind.RELEARNING:
                return "revlog-relearn";
        }
        return "";
    }

    function reviewKindLabel(entry: RevlogEntry): string {
        switch (entry.reviewKind) {
            case ReviewKind.LEARNING:
                return tr2.cardStatsReviewLogTypeLearn();
            case ReviewKind.REVIEW:
                return tr2.cardStatsReviewLogTypeReview();
            case ReviewKind.RELEARNING:
                return tr2.cardStatsReviewLogTypeRelearn();
            case ReviewKind.FILTERED:
                return tr2.cardStatsReviewLogTypeFiltered();
            case ReviewKind.MANUAL:
                return tr2.cardStatsReviewLogTypeManual();
            case ReviewKind.RESCHEDULED:
                return tr2.cardStatsReviewLogTypeRescheduled();
        }
    }

    function ratingClass(entry: RevlogEntry): string {
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
        elapsedTime: string;
        stability: string;
    }

    function revlogRowFromEntry(entry: RevlogEntry, elapsedTime: string): RevlogRow {
        const timestamp = new Timestamp(Number(entry.time));

        return {
            date: timestamp.dateString(),
            time: timestamp.timeString(),
            reviewKind: reviewKindLabel(entry),
            reviewKindClass: reviewKindClass(entry),
            rating: entry.buttonChosen,
            ratingClass: ratingClass(entry),
            interval: timeSpan(entry.interval),
            ease: formatEaseOrDifficulty(entry.ease),
            takenSecs: timeSpan(entry.takenSecs, true),
            elapsedTime,
            stability: entry.memoryState?.stability
                ? timeSpan(entry.memoryState.stability * 86400)
                : "",
        };
    }

    $: revlogRows = revlog.map((entry, index) => {
        let prevValidEntry: RevlogEntry | undefined;
        let i = index + 1;
        while (i < revlog.length) {
            if (filterRevlogEntryByReviewKind(revlog[i])) {
                prevValidEntry = revlog[i];
                break;
            }
            i++;
        }

        let elapsedTime = "N/A";
        if (filterRevlogEntryByReviewKind(entry)) {
            elapsedTime = prevValidEntry
                ? timeSpan(Number(entry.time) - Number(prevValidEntry.time))
                : "0";
        }

        return revlogRowFromEntry(entry, elapsedTime);
    });

    function formatEaseOrDifficulty(ease: number): string {
        if (ease === 0) {
            return "";
        }
        const asPct = ease / 10.0;
        if (asPct <= 110) {
            // FSRS
            const unshifted = asPct - 10;
            return `D:${unshifted.toFixed(0)}%`;
        } else {
            // SM-2
            return `${asPct.toFixed(0)}%`;
        }
    }
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
        <!-- {#if fsrsEnabled}
            <div class="column">
                <div class="column-head">{tr2.cardStatsFsrsStability()}</div>
                <div class="column-content right">
                    {#each revlogRows as row, _index}
                        <div>{row.stability}</div>
                    {/each}
                </div>
            </div>
        {/if} -->
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
