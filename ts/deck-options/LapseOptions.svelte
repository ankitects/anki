<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
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
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <StepsInputRow
                bind:value={$config.relearnSteps}
                defaultValue={defaults.relearnSteps}
                markdownTooltip={tr.deckConfigRelearningStepsTooltip()}
            >
                {tr.deckConfigRelearningSteps()}
            </StepsInputRow>
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.minimumLapseInterval}
                defaultValue={defaults.minimumLapseInterval}
                min={1}
                markdownTooltip={tr.deckConfigMinimumIntervalTooltip()}
            >
                {tr.schedulingMinimumInterval()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={stepsExceedMinimumInterval} />
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.leechThreshold}
                defaultValue={defaults.leechThreshold}
                min={1}
                markdownTooltip={tr.deckConfigLeechThresholdTooltip()}
            >
                {tr.schedulingLeechThreshold()}
            </SpinBoxRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.leechAction}
                defaultValue={defaults.leechAction}
                choices={leechChoices}
                breakpoint="sm"
                markdownTooltip={tr.deckConfigLeechActionTooltip()}
            >
                {tr.schedulingLeechAction()}
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
