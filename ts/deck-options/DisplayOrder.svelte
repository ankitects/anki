<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import Item from "components/Item.svelte";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";

    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    let config = state.currentConfig;
    let defaults = state.defaults;

    let currentDeck = "\n\n" + tr.deckConfigDisplayOrderWillUseCurrentDeck();

    const newGatherPriorityChoices = [
        tr.deckConfigNewGatherPriorityDeck(),
        tr.deckConfigNewGatherPriorityPositionLowestFirst(),
        tr.deckConfigNewGatherPriorityPositionHighestFirst(),
    ];
    const newSortOrderChoices = [
        tr.deckConfigSortOrderCardTemplateThenLowestPosition(),
        tr.deckConfigSortOrderCardTemplateThenHighestPosition(),
        tr.deckConfigSortOrderCardTemplateThenRandom(),
        tr.deckConfigSortOrderLowestPosition(),
        tr.deckConfigSortOrderHighestPosition(),
        tr.deckConfigSortOrderRandom(),
    ];
    const reviewOrderChoices = [
        tr.deckConfigSortOrderDueDateThenRandom(),
        tr.deckConfigSortOrderDueDateThenDeck(),
        tr.deckConfigSortOrderDeckThenDueDate(),
        tr.deckConfigSortOrderAscendingIntervals(),
        tr.deckConfigSortOrderDescendingIntervals(),
    ];
</script>

<TitledContainer title={tr.deckConfigOrderingTitle()} {api}>
    <Item>
        <EnumSelectorRow
            bind:value={$config.newCardGatherPriority}
            defaultValue={defaults.newCardGatherPriority}
            choices={newGatherPriorityChoices}
            markdownTooltip={tr.deckConfigNewGatherPriorityTooltip() + currentDeck}
        >
            {tr.deckConfigNewGatherPriority()}
        </EnumSelectorRow>
    </Item>

    <Item>
        <EnumSelectorRow
            bind:value={$config.newCardSortOrder}
            defaultValue={defaults.newCardSortOrder}
            choices={newSortOrderChoices}
            markdownTooltip={tr.deckConfigNewCardSortOrderTooltip() + currentDeck}
        >
            {tr.deckConfigNewCardSortOrder()}
        </EnumSelectorRow>
    </Item>

    <Item>
        <EnumSelectorRow
            bind:value={$config.newMix}
            defaultValue={defaults.newMix}
            choices={reviewMixChoices()}
            markdownTooltip={tr.deckConfigNewReviewPriorityTooltip() + currentDeck}
        >
            {tr.deckConfigNewReviewPriority()}
        </EnumSelectorRow>
    </Item>

    <Item>
        <EnumSelectorRow
            bind:value={$config.interdayLearningMix}
            defaultValue={defaults.interdayLearningMix}
            choices={reviewMixChoices()}
            markdownTooltip={tr.deckConfigInterdayStepPriorityTooltip() + currentDeck}
        >
            {tr.deckConfigInterdayStepPriority()}
        </EnumSelectorRow>
    </Item>

    <Item>
        <EnumSelectorRow
            bind:value={$config.reviewOrder}
            defaultValue={defaults.reviewOrder}
            choices={reviewOrderChoices}
            markdownTooltip={tr.deckConfigReviewSortOrderTooltip() + currentDeck}
        >
            {tr.deckConfigReviewSortOrder()}
        </EnumSelectorRow>
    </Item>
</TitledContainer>
