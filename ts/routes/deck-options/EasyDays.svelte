<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Item from "$lib/components/Item.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { DeckOptionsState } from "./lib";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    $: if ($config.easyDaysPercentages.length !== 7) {
        $config.easyDaysPercentages = defaults.easyDaysPercentages.slice();
    }

    $: noNormalDay = $config.easyDaysPercentages.some((p) => p === 1.0)
        ? ""
        : tr.deckConfigEasyDaysNoNormalDays();

    const easyDays = [
        tr.deckConfigEasyDaysMonday(),
        tr.deckConfigEasyDaysTuesday(),
        tr.deckConfigEasyDaysWednesday(),
        tr.deckConfigEasyDaysThursday(),
        tr.deckConfigEasyDaysFriday(),
        tr.deckConfigEasyDaysSaturday(),
        tr.deckConfigEasyDaysSunday(),
    ];
</script>

<datalist id="easy_day_steplist">
    <option>0.5</option>
</datalist>

<TitledContainer title={tr.deckConfigEasyDaysTitle()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <div class="easy-days-settings">
                <table>
                    <thead>
                        <tr>
                            <th></th>
                            <th colspan="3">
                                <div class="header">
                                    <span>{tr.deckConfigEasyDaysMinimum()}</span>
                                    <span>{tr.deckConfigEasyDaysReduced()}</span>
                                    <span>{tr.deckConfigEasyDaysNormal()}</span>
                                </div>
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each easyDays as day, index}
                            <tr>
                                <td>{day}</td>
                                <td colspan="3">
                                    <input
                                        type="range"
                                        bind:value={$config.easyDaysPercentages[index]}
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
        <Item>
            <Warning warning={noNormalDay} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>

<style>
    .easy-days-settings table {
        width: 100%;
        border-collapse: collapse;
    }
    .easy-days-settings th,
    .easy-days-settings td {
        padding: 8px;
        text-align: center;
        border-bottom: var(--border) solid 1px;
    }
    .header {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        word-wrap: break-word;
        font-size: smaller;
    }
    .header span:nth-child(1) {
        text-align: left;
    }
    .header span:nth-child(3) {
        text-align: right;
    }
    .easy-days-settings input[type="range"] {
        width: 100%;
    }
</style>
