<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import TitledContainer from "./TitledContainer.svelte";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";

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

    let disabledNewSortOrders: number[] = [];
    $: {
        switch ($config.newCardGatherPriority) {
            case 3:
                // random notes
                // - 'template, gather' is the same as 'template, random' (2)
                // - 'gather' is the same as 'random note, template' (3)
                disabledNewSortOrders = [2, 3];
                break;
            case 4:
                // random cards
                // - 'template, gather' is the same as 'template, random' (2)
                // - 'random note, template' is not useful if siblings are not
                //    gathered together (3)
                // - 'gather' is the same as 'random' (4)
                disabledNewSortOrders = [2, 3, 4];
                break;
            default:
                disabledNewSortOrders = [];
                break;
        }
        if (disabledNewSortOrders.includes($config.newCardSortOrder)) {
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
                disabled={disabledNewSortOrders}
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
