<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr2 from "@tslib/ftl";
    import type { Stats } from "@tslib/proto";
    import { DAY, timeSpan, Timestamp } from "@tslib/time";

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

        if (stats.firstReview != null) {
            statsRows.push({
                label: tr2.cardStatsFirstReview(),
                value: dateString(stats.firstReview),
            });
        }
        if (stats.latestReview != null) {
            statsRows.push({
                label: tr2.cardStatsLatestReview(),
                value: dateString(stats.latestReview),
            });
        }

        if (stats.dueDate != null) {
            statsRows.push({
                label: tr2.statisticsDueDate(),
                value: dateString(stats.dueDate),
            });
        }
        if (stats.duePosition != null) {
            statsRows.push({
                label: tr2.cardStatsNewCardPosition(),
                value: stats.duePosition,
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

        if (stats.customData) {
            let value: string;
            try {
                const obj = JSON.parse(stats.customData);
                value = Object.entries(obj)
                    .map(([k, v]) => `${k}=${v}`)
                    .join(" ");
            } catch (exc) {
                value = stats.customData;
            }
            statsRows.push({
                label: tr2.cardStatsCustomData(),
                value: value,
            });
        }

        return statsRows;
    }

    let statsRows: StatsRow[];
    $: statsRows = rowsFromStats(stats);
</script>

<table class="stats-table align-start">
    {#each statsRows as row}
        <tr>
            <th class="align-start">{row.label}</th>
            <td>{row.value}</td>
        </tr>
    {/each}
</table>

<style>
    .stats-table {
        width: 100%;
        border-spacing: 1em 0;
        border-collapse: collapse;
    }

    .align-start {
        text-align: start;
    }
</style>
