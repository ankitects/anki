<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { CardStatsResponse } from "@generated/anki/stats_pb";
    import { rowsFromStats, type StatsRow } from "./lib";

    export let stats: CardStatsResponse;

    let statsRows: StatsRow[];
    $: statsRows = rowsFromStats(stats);
</script>

<table class="stats-table align-start">
    <tbody>
        {#each statsRows as row}
            <tr>
                <th class="align-start">{row.label}</th>
                <td>{row.value}</td>
            </tr>
        {/each}
    </tbody>
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
