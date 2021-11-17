<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TitledContainer from "./TitledContainer.svelte";
    import StepsInputRow from "./StepsInputRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import EnumSelectorRow from "./EnumSelectorRow.svelte";
    import Item from "../components/Item.svelte";
    import Warning from "./Warning.svelte";

    import type { DeckOptionsState } from "./lib";
    import * as tr from "../lib/ftl";

    export let state: DeckOptionsState;
    export let api = {};

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

<TitledContainer title={tr.schedulingNewCards()} {api}>
    <StepsInputRow
        bind:value={$config.learnSteps}
        defaultValue={defaults.learnSteps}
        markdownTooltip={tr.deckConfigLearningStepsTooltip()}
    >
        {tr.deckConfigLearningSteps()}
    </StepsInputRow>

    <SpinBoxRow
        bind:value={$config.graduatingIntervalGood}
        defaultValue={defaults.graduatingIntervalGood}
        markdownTooltip={tr.deckConfigGraduatingIntervalTooltip()}
    >
        {tr.schedulingGraduatingInterval()}
    </SpinBoxRow>

    <Item>
        <Warning warning={stepsExceedGraduatingInterval} />
    </Item>

    <SpinBoxRow
        bind:value={$config.graduatingIntervalEasy}
        defaultValue={defaults.graduatingIntervalEasy}
        markdownTooltip={tr.deckConfigEasyIntervalTooltip()}
    >
        {tr.schedulingEasyInterval()}
    </SpinBoxRow>

    <Item>
        <Warning warning={goodExceedsEasy} />
    </Item>

    <EnumSelectorRow
        bind:value={$config.newCardInsertOrder}
        defaultValue={defaults.newCardInsertOrder}
        choices={newInsertOrderChoices}
        breakpoint={"md"}
        markdownTooltip={tr.deckConfigNewInsertionOrderTooltip()}
    >
        {tr.deckConfigNewInsertionOrder()}
    </EnumSelectorRow>
</TitledContainer>
