<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type pb from "anki/backend_proto";
    import * as tr from "anki/i18n";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";

    export let config: pb.BackendProto.DeckConfig.Config;
    export let defaults: pb.BackendProto.DeckConfig.Config;

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];
</script>

<div>
    <h2>Lapses</h2>

    <StepsInput
        label="Relearning steps"
        subLabel="Relearning steps, separated by spaces."
        defaultValue={defaults.relearnSteps}
        value={config.relearnSteps}
        on:changed={(evt) => (config.relearnSteps = evt.detail.value)} />

    <SpinBoxFloat
        label={tr.schedulingNewInterval()}
        subLabel="The multiplier applied to review cards when answering Again."
        min={0}
        max={1}
        defaultValue={defaults.lapseMultiplier}
        value={config.lapseMultiplier}
        on:changed={(evt) => (config.lapseMultiplier = evt.detail.value)} />

    <SpinBox
        label={tr.schedulingMinimumInterval()}
        subLabel="The minimum new interval a lapsed card will be given after relearning."
        min={1}
        defaultValue={defaults.minimumLapseInterval}
        bind:value={config.minimumLapseInterval} />

    <SpinBox
        label={tr.schedulingLeechThreshold()}
        subLabel="Number of times Again needs to be pressed on a review card to make it a leech."
        min={1}
        defaultValue={defaults.leechThreshold}
        bind:value={config.leechThreshold} />

    <EnumSelector
        label={tr.schedulingLeechAction()}
        subLabel=""
        choices={leechChoices}
        defaultValue={defaults.leechAction}
        bind:value={config.leechAction} />
</div>
