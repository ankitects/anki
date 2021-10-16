<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "../lib/ftl";
    import { Stats, unwrapOptionalNumber } from "../lib/proto";
    import { Timestamp, timeSpan, DAY } from "../lib/time";
    import Revlog from "./Revlog.svelte";

    export let stats: Stats.CardStatsResponse;

    function dateString(timestamp: number): string {
        return new Timestamp(timestamp).dateString();
    }

    interface StatsRow {
        label: string;
        value: string | number;
    }

    function rowsFromStats(stats: Stats.CardStatsResponse): StatsRow[] {
        const statsRows: StatsRow[] = [];

        statsRows.push({ label: tr2.cardStatsAdded(), value: dateString(stats.added) });

        const firstReview = unwrapOptionalNumber(stats.firstReview);
        if (firstReview !== undefined) {
            statsRows.push({
                label: tr2.cardStatsFirstReview(),
                value: dateString(firstReview),
            });
        }
        const latestReview = unwrapOptionalNumber(stats.latestReview);
        if (latestReview !== undefined) {
            statsRows.push({
                label: tr2.cardStatsLatestReview(),
                value: dateString(latestReview),
            });
        }

        const dueDate = unwrapOptionalNumber(stats.dueDate);
        if (dueDate !== undefined) {
            statsRows.push({
                label: tr2.statisticsDueDate(),
                value: dateString(dueDate),
            });
        }
        const duePosition = unwrapOptionalNumber(stats.duePosition);
        if (duePosition !== undefined) {
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
            statsRows.push({
                label: tr2.cardStatsEase(),
                value: `${stats.ease / 10}%`,
            });
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

        return statsRows;
    }

    let statsRows: StatsRow[];
    $: statsRows = rowsFromStats(stats);
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
        <Revlog {stats} />
    </div>
</div>

<style>
    .container {
        max-width: 40em;
    }

    .stats-table {
        width: 100%;
        border-spacing: 1em 0;
        border-collapse: collapse;
        text-align: start;
    }
</style>
