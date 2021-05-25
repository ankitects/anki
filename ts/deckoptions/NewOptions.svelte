<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    const newInsertOrderChoices = [
        tr.deckConfigNewInsertionOrderSequential(),
        tr.deckConfigNewInsertionOrderRandom(),
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

<StepsInput
    label={tr.deckConfigLearningSteps()}
    tooltip={tr.deckConfigLearningStepsTooltip()}
    defaultValue={defaults.learnSteps}
    value={$config.learnSteps}
    on:changed={(evt) => ($config.learnSteps = evt.detail.value)}
/>

<SpinBox
    label={tr.schedulingGraduatingInterval()}
    tooltip={tr.deckConfigGraduatingIntervalTooltip()}
    warnings={[stepsExceedGraduatingInterval]}
    defaultValue={defaults.graduatingIntervalGood}
    bind:value={$config.graduatingIntervalGood}
/>

<SpinBox
    label={tr.schedulingEasyInterval()}
    tooltip={tr.deckConfigEasyIntervalTooltip()}
    warnings={[goodExceedsEasy]}
    defaultValue={defaults.graduatingIntervalEasy}
    bind:value={$config.graduatingIntervalEasy}
/>

<EnumSelector
    label={tr.deckConfigNewInsertionOrder()}
    tooltip={tr.deckConfigNewInsertionOrderTooltip()}
    choices={newInsertOrderChoices}
    defaultValue={defaults.newCardInsertOrder}
    bind:value={$config.newCardInsertOrder}
/>
