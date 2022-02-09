<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import { DeckConfig } from "../lib/proto";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";
    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";
    import TitledContainer from "./TitledContainer.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const currentDeck = "\n\n" + tr.deckConfigDisplayOrderWillUseCurrentDeck();

    const newGatherPriorityChoices = [
        tr.deckConfigNewGatherPriorityDeck(),
        tr.deckConfigNewGatherPriorityPositionLowestFirst(),
        tr.deckConfigNewGatherPriorityPositionHighestFirst(),
        tr.deckConfigNewGatherPriorityRandomNotes(),
        tr.deckConfigNewGatherPriorityRandomCards(),
    ];
    const newSortOrderChoices = [
        tr.deckConfigSortOrderTemplateThenGather(),
        tr.deckConfigSortOrderGather(),
        tr.deckConfigSortOrderCardTemplateThenRandom(),
        tr.deckConfigSortOrderRandomNoteThenTemplate(),
        tr.deckConfigSortOrderRandom(),
    ];
    const reviewOrderChoices = [
        tr.deckConfigSortOrderDueDateThenRandom(),
        tr.deckConfigSortOrderDueDateThenDeck(),
        tr.deckConfigSortOrderDeckThenDueDate(),
        tr.deckConfigSortOrderAscendingIntervals(),
        tr.deckConfigSortOrderDescendingIntervals(),
        tr.deckConfigSortOrderAscendingEase(),
        tr.deckConfigSortOrderDescendingEase(),
    ];

    const GatherOrder = DeckConfig.DeckConfig.Config.NewCardGatherPriority;
    const SortOrder = DeckConfig.DeckConfig.Config.NewCardSortOrder;
    let disabledNewSortOrders: number[] = [];
    $: {
        switch ($config.newCardGatherPriority) {
            case GatherOrder.NEW_CARD_GATHER_PRIORITY_RANDOM_NOTES:
                disabledNewSortOrders = [
                    // same as NEW_CARD_SORT_ORDER_TEMPLATE
                    SortOrder.NEW_CARD_SORT_ORDER_TEMPLATE_THEN_RANDOM,
                    // same as NEW_CARD_SORT_ORDER_NO_SORT
                    SortOrder.NEW_CARD_SORT_ORDER_RANDOM_NOTE_THEN_TEMPLATE,
                ];
                break;
            case GatherOrder.NEW_CARD_GATHER_PRIORITY_RANDOM_CARDS:
                disabledNewSortOrders = [
                    // same as NEW_CARD_SORT_ORDER_TEMPLATE
                    SortOrder.NEW_CARD_SORT_ORDER_TEMPLATE_THEN_RANDOM,
                    // not useful if siblings are not gathered together
                    SortOrder.NEW_CARD_SORT_ORDER_RANDOM_NOTE_THEN_TEMPLATE,
                    // same as NEW_CARD_SORT_ORDER_NO_SORT
                    SortOrder.NEW_CARD_SORT_ORDER_RANDOM_CARD,
                ];
                break;
            default:
                disabledNewSortOrders = [];
                break;
        }

        // disabled options aren't deselected automatically
        if (disabledNewSortOrders.includes($config.newCardSortOrder)) {
            // default option should never be disabled
            $config.newCardSortOrder = 0;
        }

        // check for invalid index from previous version
        if (!($config.newCardSortOrder in SortOrder)) {
            $config.newCardSortOrder = 0;
        }
    }
</script>

<TitledContainer title={tr.deckConfigOrderingTitle()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardGatherPriority}
                defaultValue={defaults.newCardGatherPriority}
                choices={newGatherPriorityChoices}
                markdownTooltip={tr.deckConfigNewGatherPriorityTooltip_2() +
                    currentDeck}
            >
                {tr.deckConfigNewGatherPriority()}
            </EnumSelectorRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardSortOrder}
                defaultValue={defaults.newCardSortOrder}
                choices={newSortOrderChoices}
                disabled={disabledNewSortOrders}
                markdownTooltip={tr.deckConfigNewCardSortOrderTooltip_2() + currentDeck}
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
                markdownTooltip={tr.deckConfigInterdayStepPriorityTooltip() +
                    currentDeck}
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
    </DynamicallySlottable>
</TitledContainer>
