<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOptionsState } from "./lib";
    import SwitchRow from "./SwitchRow.svelte";
    import TitledContainer from "./TitledContainer.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SwitchRow
                bind:value={$config.buryNew}
                defaultValue={defaults.buryNew}
                markdownTooltip={tr.deckConfigBuryTooltip()}
            >
                {tr.deckConfigBuryNewSiblings()}
            </SwitchRow>
        </Item>

        <Item>
            <SwitchRow
                bind:value={$config.buryReviews}
                defaultValue={defaults.buryReviews}
                markdownTooltip={tr.deckConfigBuryTooltip()}
            >
                {tr.deckConfigBuryReviewSiblings()}
            </SwitchRow>
        </Item>

        {#if state.v3Scheduler}
            <Item>
                <SwitchRow
                    bind:value={$config.buryInterdayLearning}
                    defaultValue={defaults.buryInterdayLearning}
                    markdownTooltip={tr.deckConfigBuryTooltip()}
                >
                    {tr.deckConfigBuryInterdayLearningSiblings()}
                </SwitchRow>
            </Item>
        {/if}
    </DynamicallySlottable>
</TitledContainer>
