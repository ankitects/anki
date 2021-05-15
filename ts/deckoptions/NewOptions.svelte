<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
    import CheckBox from "./CheckBox.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    const newInsertOrderChoices = [
        tr.deckConfigNewInsertionOrderSequential(),
        tr.deckConfigNewInsertionOrderRandom(),
    ];
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

    let stepsExceedGraduatingInterval: string;
    $: {
        const lastLearnStepInDays = $config.learnSteps.length
            ? $config.learnSteps[$config.learnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedGraduatingInterval =
            lastLearnStepInDays > $config.graduatingIntervalGood
                ? tr.deckConfigLearningStepAboveGraduatingInterval()
                : "";
    }

    $: goodExceedsEasy =
        $config.graduatingIntervalGood > $config.graduatingIntervalEasy
            ? tr.deckConfigGoodAboveEasy()
            : "";
</script>

<h2>{tr.schedulingNewCards()}</h2>

<SpinBox
    label={tr.schedulingGraduatingInterval()}
    tooltip={tr.deckConfigGraduatingIntervalTooltip()}
    warnings={[stepsExceedGraduatingInterval]}
    defaultValue={defaults.graduatingIntervalGood}
    bind:value={$config.graduatingIntervalGood} />

<SpinBox
    label={tr.schedulingEasyInterval()}
    tooltip={tr.deckConfigEasyIntervalTooltip()}
    warnings={[goodExceedsEasy]}
    defaultValue={defaults.graduatingIntervalEasy}
    bind:value={$config.graduatingIntervalEasy} />

{#if state.v3Scheduler}
    <EnumSelector
        label={tr.deckConfigNewInsertionOrder()}
        tooltip={tr.deckConfigNewInsertionOrderTooltip()}
        choices={newInsertOrderChoices}
        defaultValue={defaults.newCardInsertOrder}
        bind:value={$config.newCardInsertOrder} />

    <EnumSelector
        label={tr.deckConfigNewGatherPriority()}
        tooltip={tr.deckConfigNewGatherPriorityTooltip()}
        choices={newGatherPriorityChoices}
        defaultValue={defaults.newCardGatherPriority}
        bind:value={$config.newCardGatherPriority} />

    <EnumSelector
        label={tr.deckConfigSortOrder()}
        tooltip={tr.deckConfigSortOrderTooltip()}
        choices={newSortOrderChoices}
        defaultValue={defaults.newCardSortOrder}
        bind:value={$config.newCardSortOrder} />

    <EnumSelector
        label={tr.deckConfigReviewPriority()}
        tooltip={tr.deckConfigReviewPriorityTooltip()}
        choices={reviewMixChoices()}
        defaultValue={defaults.newMix}
        bind:value={$config.newMix} />
{/if}

<CheckBox
    label={tr.deckConfigBuryNewSiblings()}
    tooltip={tr.deckConfigBuryTooltip()}
    defaultValue={defaults.buryNew}
    bind:value={$config.buryNew} />
