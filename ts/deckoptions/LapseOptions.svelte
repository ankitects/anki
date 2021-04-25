<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];
</script>

<div>
    <h2>Lapses</h2>

    <StepsInput
        label="Relearning steps"
        tooltip="Relearning steps, separated by spaces."
        defaultValue={defaults.relearnSteps}
        value={$config.relearnSteps}
        on:changed={(evt) => ($config.relearnSteps = evt.detail.value)} />

    <SpinBoxFloat
        label={tr.schedulingNewInterval()}
        tooltip="The multiplier applied to review cards when answering Again."
        min={0}
        max={1}
        defaultValue={defaults.lapseMultiplier}
        value={$config.lapseMultiplier}
        on:changed={(evt) => ($config.lapseMultiplier = evt.detail.value)} />

    <SpinBox
        label={tr.schedulingMinimumInterval()}
        tooltip="The minimum new interval a lapsed card will be given after relearning."
        min={1}
        defaultValue={defaults.minimumLapseInterval}
        bind:value={$config.minimumLapseInterval} />

    <SpinBox
        label={tr.schedulingLeechThreshold()}
        tooltip="Number of times Again needs to be pressed on a review card to make it a leech."
        min={1}
        defaultValue={defaults.leechThreshold}
        bind:value={$config.leechThreshold} />

    <EnumSelector
        label={tr.schedulingLeechAction()}
        tooltip=""
        choices={leechChoices}
        defaultValue={defaults.leechAction}
        bind:value={$config.leechAction} />
</div>
