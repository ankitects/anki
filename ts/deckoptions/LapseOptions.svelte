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
    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigRelearningSteps()}<HelpPopup
                html={marked(tr.deckConfigRelearningStepsTooltip())}
            />
        </span>
        <svelte:fragment slot="center">
            <StepsInput
                value={$config.relearnSteps}
                on:changed={(evt) => ($config.relearnSteps = evt.detail.value)}
            />
        </svelte:fragment>
        <svelte:fragment slot="right">
            <RevertButton
                defaultValue={defaults.relearnSteps}
                value={$config.relearnSteps}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingMinimumInterval()}<HelpPopup
                html={marked(tr.deckConfigMinimumIntervalTooltip())}
            />
        </span>
        <svelte:fragment slot="right">
            <SpinBox min={1} bind:value={$config.minimumLapseInterval} />
            <RevertButton
                defaultValue={defaults.minimumLapseInterval}
                bind:value={$config.minimumLapseInterval}
            />
        </svelte:fragment>
    </ConfigEntry>

    <Warnings warnings={[stepsExceedMinimumInterval]} />

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingLeechThreshold()}<HelpPopup
                html={marked(tr.deckConfigLeechThresholdTooltip())}
            />
        </span>
        <svelte:fragment slot="center">
            <SpinBox min={1} bind:value={$config.leechThreshold} />
        </svelte:fragment>
        <svelte:fragment slot="right">
            <RevertButton
                defaultValue={defaults.leechThreshold}
                bind:value={$config.leechThreshold}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingLeechAction()}<HelpPopup
                html={marked(tr.deckConfigLeechActionTooltip())}
            />
        </span>
        <svelte:fragment slot="center">
            <EnumSelector choices={leechChoices} bind:value={$config.leechAction} />
        </svelte:fragment>
        <svelte:fragment slot="right">
            <RevertButton
                defaultValue={defaults.leechAction}
                bind:value={$config.leechAction}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>
