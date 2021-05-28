<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";

    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
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

<TitledContainer title={tr.deckConfigOrderingTitle()}>
    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigNewGatherPriority()}
            <HelpPopup html={marked(tr.deckConfigNewGatherPriorityTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector
                choices={newGatherPriorityChoices}
                bind:value={$config.newCardGatherPriority}
            />
            <RevertButton
                defaultValue={defaults.newCardGatherPriority}
                bind:value={$config.newCardGatherPriority}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigNewCardSortOrder()}
            <HelpPopup html={marked(tr.deckConfigNewCardSortOrderTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector
                choices={newSortOrderChoices}
                bind:value={$config.newCardSortOrder}
            />
            <RevertButton
                defaultValue={defaults.newCardSortOrder}
                bind:value={$config.newCardSortOrder}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigNewReviewPriority()}
            <HelpPopup html={marked(tr.deckConfigNewReviewPriorityTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector choices={reviewMixChoices()} bind:value={$config.newMix} />
            <RevertButton defaultValue={defaults.newMix} bind:value={$config.newMix} />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigInterdayStepPriority()}
            <HelpPopup html={marked(tr.deckConfigInterdayStepPriorityTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector
                choices={reviewMixChoices()}
                bind:value={$config.interdayLearningMix}
            />
            <RevertButton
                defaultValue={defaults.interdayLearningMix}
                bind:value={$config.interdayLearningMix}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigReviewSortOrder()}
            <HelpPopup html={marked(tr.deckConfigReviewSortOrderTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector
                choices={reviewOrderChoices}
                bind:value={$config.reviewOrder}
            />
            <RevertButton
                defaultValue={defaults.reviewOrder}
                bind:value={$config.reviewOrder}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>>
