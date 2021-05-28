<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import Warnings from "./Warnings.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import SpinBox from "./SpinBox.svelte";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";
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

<TitledContainer title={tr.schedulingNewCards()}>
    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigLearningSteps()}<HelpPopup
                html={marked(tr.deckConfigLearningStepsTooltip())}
            />
        </span>
        <svelte:fragment slot="right">
            <StepsInput bind:value={$config.learnSteps} />
            <RevertButton
                defaultValue={defaults.learnSteps}
                bind:value={$config.learnSteps}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingGraduatingInterval()}<HelpPopup
                html={marked(tr.deckConfigGraduatingIntervalTooltip())}
            />
        </span>
        <svelte:fragment slot="right">
            <SpinBox bind:value={$config.graduatingIntervalGood} />
            <RevertButton
                defaultValue={defaults.graduatingIntervalGood}
                bind:value={$config.graduatingIntervalGood}
            />
        </svelte:fragment>
    </ConfigEntry>

    <Warnings warnings={[stepsExceedGraduatingInterval]} />

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingEasyInterval()}<HelpPopup
                html={marked(tr.deckConfigEasyIntervalTooltip())}
            />
        </span>
        <svelte:fragment slot="right">
            <SpinBox bind:value={$config.graduatingIntervalEasy} />
            <RevertButton
                defaultValue={defaults.graduatingIntervalEasy}
                bind:value={$config.graduatingIntervalEasy}
            />
        </svelte:fragment>
    </ConfigEntry>

    <Warnings warnings={[goodExceedsEasy]} />

    <ConfigEntry wrap={true}>
        <span slot="left">
            {tr.deckConfigNewInsertionOrder()}<HelpPopup
                html={marked(tr.deckConfigNewInsertionOrderTooltip())}
            />
        </span>
        <svelte:fragment slot="right">
            <EnumSelector
                choices={newInsertOrderChoices}
                bind:value={$config.newCardInsertOrder}
            />
            <RevertButton
                defaultValue={defaults.newCardInsertOrder}
                bind:value={$config.newCardInsertOrder}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>
