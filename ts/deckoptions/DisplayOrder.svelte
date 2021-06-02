<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import EnumSelector from "./EnumSelector.svelte";

    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    const newGatherPriorityChoices = [
        tr.deckConfigNewGatherPriorityDeck(),
        tr.deckConfigNewGatherPriorityPosition(),
    ];
    const newSortOrderChoices = [
        tr.deckConfigSortOrderCardTemplateThenPosition(),
        tr.deckConfigSortOrderCardTemplateThenRandom(),
        tr.deckConfigSortOrderPosition(),
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

<h2>{tr.deckConfigOrderingTitle()}</h2>

<EnumSelector
    label={tr.deckConfigNewGatherPriority()}
    tooltip={tr.deckConfigNewGatherPriorityTooltip()}
    choices={newGatherPriorityChoices}
    defaultValue={defaults.newCardGatherPriority}
    bind:value={$config.newCardGatherPriority}
/>

<EnumSelector
    label={tr.deckConfigNewCardSortOrder()}
    tooltip={tr.deckConfigNewCardSortOrderTooltip()}
    choices={newSortOrderChoices}
    defaultValue={defaults.newCardSortOrder}
    bind:value={$config.newCardSortOrder}
/>

<EnumSelector
    label={tr.deckConfigNewReviewPriority()}
    tooltip={tr.deckConfigNewReviewPriorityTooltip()}
    choices={reviewMixChoices()}
    defaultValue={defaults.newMix}
    bind:value={$config.newMix}
/>

<EnumSelector
    label={tr.deckConfigInterdayStepPriority()}
    tooltip={tr.deckConfigInterdayStepPriorityTooltip()}
    choices={reviewMixChoices()}
    defaultValue={defaults.interdayLearningMix}
    bind:value={$config.interdayLearningMix}
/>

<EnumSelector
    label={tr.deckConfigReviewSortOrder()}
    tooltip={tr.deckConfigReviewSortOrderTooltip()}
    choices={reviewOrderChoices}
    defaultValue={defaults.reviewOrder}
    bind:value={$config.reviewOrder}
/>
