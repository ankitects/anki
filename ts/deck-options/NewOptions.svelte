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
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import StepsInputRow from "./StepsInputRow.svelte";
    import TitledContainer from "./TitledContainer.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api = {};

    const config = state.currentConfig;
    const defaults = state.defaults;

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

    $: insertionOrderRandom =
        state.v3Scheduler &&
        $config.newCardInsertOrder ==
            DeckConfig.DeckConfig.Config.NewCardInsertOrder.NEW_CARD_INSERT_ORDER_RANDOM
            ? tr.deckConfigNewInsertionOrderRandomWithV3()
            : "";
</script>

<TitledContainer title={tr.schedulingNewCards()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <StepsInputRow
                bind:value={$config.learnSteps}
                defaultValue={defaults.learnSteps}
                markdownTooltip={tr.deckConfigLearningStepsTooltip()}
            >
                {tr.deckConfigLearningSteps()}
            </StepsInputRow>
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.graduatingIntervalGood}
                defaultValue={defaults.graduatingIntervalGood}
                markdownTooltip={tr.deckConfigGraduatingIntervalTooltip()}
            >
                {tr.schedulingGraduatingInterval()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={stepsExceedGraduatingInterval} />
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.graduatingIntervalEasy}
                defaultValue={defaults.graduatingIntervalEasy}
                markdownTooltip={tr.deckConfigEasyIntervalTooltip()}
            >
                {tr.schedulingEasyInterval()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={goodExceedsEasy} />
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardInsertOrder}
                defaultValue={defaults.newCardInsertOrder}
                choices={newInsertOrderChoices}
                breakpoint={"md"}
                markdownTooltip={tr.deckConfigNewInsertionOrderTooltip()}
            >
                {tr.deckConfigNewInsertionOrder()}
            </EnumSelectorRow>
        </Item>

        <Item>
            <Warning warning={insertionOrderRandom} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
