<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import * as tr from "@generated/ftl";
    import Item from "$lib/components/Item.svelte";

    const easyDays = [
        tr.deckConfigEasyDaysMonday(),
        tr.deckConfigEasyDaysTuesday(),
        tr.deckConfigEasyDaysWednesday(),
        tr.deckConfigEasyDaysThursday(),
        tr.deckConfigEasyDaysFriday(),
        tr.deckConfigEasyDaysSaturday(),
        tr.deckConfigEasyDaysSunday(),
    ];

    export let values = [0, 0, 0, 0, 0, 0, 0];
</script>

<Item>
    <div class="easy-days-settings">
        <table>
            <thead>
                <tr>
                    <th></th>
                    <th class="header min-col">
                        <span>{tr.deckConfigEasyDaysMinimum()}</span>
                    </th>
                    <th class="header text-center">
                        <span>{tr.deckConfigEasyDaysReduced()}</span>
                    </th>
                    <th class="header normal-col">
                        <span>{tr.deckConfigEasyDaysNormal()}</span>
                    </th>
                </tr>
            </thead>
            <tbody>
                {#each easyDays as day, index}
                    <tr>
                        <td class="day">{day}</td>
                        <td colspan="3">
                            <input
                                type="range"
                                bind:value={values[index]}
                                step={0.5}
                                max={1.0}
                                min={0.0}
                                list="easy_day_steplist"
                            />
                        </td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
</Item>

<style>
    .easy-days-settings table {
        width: 100%;
        border-collapse: collapse;
        table-layout: fixed;
    }
    .easy-days-settings th,
    .easy-days-settings td {
        padding: 8px;
        border-bottom: var(--border) solid 1px;
    }
    .header {
        word-wrap: break-word;
        font-size: smaller;
    }
    .easy-days-settings input[type="range"] {
        width: 100%;
    }

    .day {
        word-wrap: break-word;
        font-size: smaller;
    }

    .min-col {
        text-align: start;
    }

    .normal-col {
        text-align: end;
    }
</style>
