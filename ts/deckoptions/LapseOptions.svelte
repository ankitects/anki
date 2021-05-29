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
    import Warning from "./Warning.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import SpinBox from "./SpinBox.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import StepsInput from "./StepsInput.svelte";
    import RevertButton from "./RevertButton.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    let stepsExceedMinimumInterval: string;
    $: {
        const lastRelearnStepInDays = $config.relearnSteps.length
            ? $config.relearnSteps[$config.relearnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedMinimumInterval =
            lastRelearnStepInDays > $config.minimumLapseInterval
                ? tr.deckConfigRelearningStepsAboveMinimumInterval()
                : "";
    }

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];
</script>

<TitledContainer title={tr.schedulingLapses()}>
    <Row>
        <Col size={7}>
            {tr.deckConfigRelearningSteps()}<HelpPopup
                html={marked(tr.deckConfigRelearningStepsTooltip())}
            />
        </Col>
        <Col breakpoint={$config.relearnSteps.length > 2 ? "sm" : undefined} size={5}>
            <StepsInput bind:value={$config.relearnSteps} />
            <RevertButton
                defaultValue={defaults.relearnSteps}
                value={$config.relearnSteps}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingMinimumInterval()}<HelpPopup
                html={marked(tr.deckConfigMinimumIntervalTooltip())}
            />
        </Col>
        <Col size={5}>
            <SpinBox min={1} bind:value={$config.minimumLapseInterval} />
            <RevertButton
                defaultValue={defaults.minimumLapseInterval}
                bind:value={$config.minimumLapseInterval}
            />
        </Col>
    </Row>

    <Warning warning={stepsExceedMinimumInterval} />

    <Row>
        <Col size={7}>
            {tr.schedulingLeechThreshold()}<HelpPopup
                html={marked(tr.deckConfigLeechThresholdTooltip())}
            />
        </Col>
        <Col size={5}>
            <SpinBox min={1} bind:value={$config.leechThreshold} />
            <RevertButton
                defaultValue={defaults.leechThreshold}
                bind:value={$config.leechThreshold}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingLeechAction()}<HelpPopup
                html={marked(tr.deckConfigLeechActionTooltip())}
            />
        </Col>
        <Col breakpoint="sm" size={5}>
            <EnumSelector choices={leechChoices} bind:value={$config.leechAction} />
            <RevertButton
                defaultValue={defaults.leechAction}
                bind:value={$config.leechAction}
            />
        </Col>
    </Row>
</TitledContainer>
