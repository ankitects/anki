<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";

    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    let config = state.currentConfig;
    let defaults = state.defaults;

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
    <EnumSelectorRow
        bind:value={$config.newCardGatherPriority}
        defaultValue={defaults.newCardGatherPriority}
        choices={newGatherPriorityChoices}
        markdownTooltip={tr.deckConfigNewGatherPriorityTooltip()}
    >
        {tr.deckConfigNewGatherPriority()}
    </EnumSelectorRow>

    <EnumSelectorRow
        bind:value={$config.newCardSortOrder}
        defaultValue={defaults.newCardSortOrder}
        choices={newSortOrderChoices}
        markdownTooltip={tr.deckConfigNewCardSortOrderTooltip()}
    >
        {tr.deckConfigNewCardSortOrder()}
    </EnumSelectorRow>

    <EnumSelectorRow
        bind:value={$config.newMix}
        defaultValue={defaults.newMix}
        choices={reviewMixChoices()}
        markdownTooltip={tr.deckConfigNewReviewPriorityTooltip()}
    >
        {tr.deckConfigNewReviewPriority()}
    </EnumSelectorRow>

    <EnumSelectorRow
        bind:value={$config.interdayLearningMix}
        defaultValue={defaults.interdayLearningMix}
        choices={reviewMixChoices()}
        markdownTooltip={tr.deckConfigInterdayStepPriorityTooltip()}
    >
        {tr.deckConfigInterdayStepPriority()}
    </EnumSelectorRow>

    <EnumSelectorRow
        bind:value={$config.reviewOrder}
        defaultValue={defaults.reviewOrder}
        choices={reviewOrderChoices}
        markdownTooltip={tr.deckConfigReviewSortOrderTooltip()}
    >
        {tr.deckConfigReviewSortOrder()}
    </EnumSelectorRow>
</TitledContainer>>
