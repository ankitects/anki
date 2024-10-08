<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Item from "$lib/components/Item.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const fsrs = state.fsrs;
</script>

<TitledContainer title={"Easy Days"}>
    <DynamicallySlottable slotHost={Item} {api}>
        {#if $fsrs}
            <Item>
                <div class="easy-days-settings">
                    <table>
                        <thead>
                            <tr>
                                <th></th>
                                <th>Normal</th>
                                <th>Reduced</th>
                                <th>Minimum</th>
                            </tr>
                        </thead>
                        <tbody>
                            {#each ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"] as day, index}
                                <tr>
                                    <td>{day}</td>
                                    <td>
                                        <input
                                            type="radio"
                                            bind:group={$config.easyDaysPercentages[
                                                index
                                            ]}
                                            value={1.0}
                                            checked={$config.easyDaysPercentages[
                                                index
                                            ] === 1.0}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="radio"
                                            bind:group={$config.easyDaysPercentages[
                                                index
                                            ]}
                                            value={0.5}
                                            checked={$config.easyDaysPercentages[
                                                index
                                            ] === 0.5}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="radio"
                                            bind:group={$config.easyDaysPercentages[
                                                index
                                            ]}
                                            value={0.0}
                                            checked={$config.easyDaysPercentages[
                                                index
                                            ] === 0.0}
                                        />
                                    </td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                </div>
            </Item>
        {/if}
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
        border: 1px solid #ddd;
    }
</style>
