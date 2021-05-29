<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
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
    <Row>
        <Col size={7}>
            {tr.deckConfigNewGatherPriority()}
            <HelpPopup html={marked(tr.deckConfigNewGatherPriorityTooltip())} />
        </Col>
        <Col size={5}>
            <EnumSelector
                choices={newGatherPriorityChoices}
                bind:value={$config.newCardGatherPriority}
            />
            <RevertButton
                defaultValue={defaults.newCardGatherPriority}
                bind:value={$config.newCardGatherPriority}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.deckConfigNewCardSortOrder()}
            <HelpPopup html={marked(tr.deckConfigNewCardSortOrderTooltip())} />
        </Col>
        <Col size={5}>
            <EnumSelector
                choices={newSortOrderChoices}
                bind:value={$config.newCardSortOrder}
            />
            <RevertButton
                defaultValue={defaults.newCardSortOrder}
                bind:value={$config.newCardSortOrder}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.deckConfigNewReviewPriority()}
            <HelpPopup html={marked(tr.deckConfigNewReviewPriorityTooltip())} />
        </Col>
        <Col size={5}>
            <EnumSelector choices={reviewMixChoices()} bind:value={$config.newMix} />
            <RevertButton defaultValue={defaults.newMix} bind:value={$config.newMix} />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.deckConfigInterdayStepPriority()}
            <HelpPopup html={marked(tr.deckConfigInterdayStepPriorityTooltip())} />
        </Col>
        <Col size={5}>
            <EnumSelector
                choices={reviewMixChoices()}
                bind:value={$config.interdayLearningMix}
            />
            <RevertButton
                defaultValue={defaults.interdayLearningMix}
                bind:value={$config.interdayLearningMix}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.deckConfigReviewSortOrder()}
            <HelpPopup html={marked(tr.deckConfigReviewSortOrderTooltip())} />
        </Col>
        <Col size={5}>
            <EnumSelector
                choices={reviewOrderChoices}
                bind:value={$config.reviewOrder}
            />
            <RevertButton
                defaultValue={defaults.reviewOrder}
                bind:value={$config.reviewOrder}
            />
        </Col>
    </Row>
</TitledContainer>>
