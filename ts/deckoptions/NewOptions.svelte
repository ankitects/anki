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
    <Row>
        <Col size={7}>
            {tr.deckConfigLearningSteps()}<HelpPopup
                html={marked(tr.deckConfigLearningStepsTooltip())}
            />
        </Col>
        <Col size={5}>
            <StepsInput bind:value={$config.learnSteps} />
            <RevertButton
                defaultValue={defaults.learnSteps}
                bind:value={$config.learnSteps}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingGraduatingInterval()}<HelpPopup
                html={marked(tr.deckConfigGraduatingIntervalTooltip())}
            />
        </Col>
        <Col size={5}>
            <SpinBox bind:value={$config.graduatingIntervalGood} />
            <RevertButton
                defaultValue={defaults.graduatingIntervalGood}
                bind:value={$config.graduatingIntervalGood}
            />
        </Col>
    </Row>

    <Warnings warnings={[stepsExceedGraduatingInterval]} />

    <Row>
        <Col size={7}>
            {tr.schedulingEasyInterval()}<HelpPopup
                html={marked(tr.deckConfigEasyIntervalTooltip())}
            />
        </Col>
        <Col size={5}>
            <SpinBox bind:value={$config.graduatingIntervalEasy} />
            <RevertButton
                defaultValue={defaults.graduatingIntervalEasy}
                bind:value={$config.graduatingIntervalEasy}
            />
        </Col>
    </Row>

    <Warnings warnings={[goodExceedsEasy]} />

    <Row>
        <Col size={7}>
            {tr.deckConfigNewInsertionOrder()}<HelpPopup
                html={marked(tr.deckConfigNewInsertionOrderTooltip())}
            />
        </Col>
        <Col breakpoint={"md"} size={5}>
            <EnumSelector
                choices={newInsertOrderChoices}
                bind:value={$config.newCardInsertOrder}
            />
            <RevertButton
                defaultValue={defaults.newCardInsertOrder}
                bind:value={$config.newCardInsertOrder}
            />
        </Col>
    </Row>
</TitledContainer>
