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

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];
</script>

<div>
    <h2>{tr.schedulingLapses()}</h2>

    <StepsInput
        label={tr.deckConfigRelearningSteps()}
        tooltip={tr.deckConfigRelearningStepsTooltip()}
        defaultValue={defaults.relearnSteps}
        value={$config.relearnSteps}
        on:changed={(evt) => ($config.relearnSteps = evt.detail.value)} />

    <SpinBox
        label={tr.schedulingLeechThreshold()}
        tooltip={tr.deckConfigLeechThresholdTooltip()}
        min={1}
        defaultValue={defaults.leechThreshold}
        bind:value={$config.leechThreshold} />

    <EnumSelector
        label={tr.schedulingLeechAction()}
        tooltip={tr.deckConfigLeechActionTooltip()}
        choices={leechChoices}
        defaultValue={defaults.leechAction}
        bind:value={$config.leechAction} />
</div>
