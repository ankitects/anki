<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { DeckConfig } from "@tslib/proto";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";
    import HelpModal from "./HelpModal.svelte";
    import type { DeckOptionsState } from "./lib";
    import SettingTitle from "./SettingTitle.svelte";
    import { reviewMixChoices } from "./strings";
    import type { DeckOption } from "./types";

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
        tr.deckConfigSortOrderRelativeOverdueness(),
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

    const settings = {
        newGatherPriority: {
            title: tr.deckConfigNewGatherPriority(),
            help: tr.deckConfigNewGatherPriorityTooltip_2() + currentDeck,
        },
        newCardSortOrder: {
            title: tr.deckConfigNewCardSortOrder(),
            help: tr.deckConfigNewCardSortOrderTooltip_2() + currentDeck,
        },
        newReviewPriority: {
            title: tr.deckConfigNewReviewPriority(),
            help: tr.deckConfigNewReviewPriorityTooltip() + currentDeck,
        },
        interdayStepPriority: {
            title: tr.deckConfigInterdayStepPriority(),
            help: tr.deckConfigInterdayStepPriorityTooltip() + currentDeck,
        },
        reviewSortOrder: {
            title: tr.deckConfigReviewSortOrder(),
            help: tr.deckConfigReviewSortOrderTooltip() + currentDeck,
        },
    };
    const helpSections = Object.values(settings) as DeckOption[];

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.deckConfigOrderingTitle()}>
    <HelpModal
        title={tr.deckConfigOrderingTitle()}
        url="https://docs.ankiweb.net/deck-options.html#display-order"
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardGatherPriority}
                defaultValue={defaults.newCardGatherPriority}
                choices={newGatherPriorityChoices}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("newGatherPriority"),
                        )}>{settings.newGatherPriority.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardSortOrder}
                defaultValue={defaults.newCardSortOrder}
                choices={newSortOrderChoices}
                disabled={disabledNewSortOrders}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("newCardSortOrder"),
                        )}>{settings.newCardSortOrder.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.newMix}
                defaultValue={defaults.newMix}
                choices={reviewMixChoices()}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("newReviewPriority"),
                        )}>{settings.newReviewPriority.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.interdayLearningMix}
                defaultValue={defaults.interdayLearningMix}
                choices={reviewMixChoices()}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("interdayStepPriority"),
                        )}>{settings.interdayStepPriority.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.reviewOrder}
                defaultValue={defaults.reviewOrder}
                choices={reviewOrderChoices}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("reviewSortOrder"))}
                    >{settings.reviewSortOrder.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
