<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckOptionsState } from "./lib";
    import { reviewMixChoices } from "./strings";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<h2>{tr.deckConfigLearningTitle()}</h2>

<StepsInput
    label={tr.deckConfigLearningSteps()}
    tooltip={tr.deckConfigLearningStepsTooltip()}
    defaultValue={defaults.learnSteps}
    value={$config.learnSteps}
    on:changed={(evt) => ($config.learnSteps = evt.detail.value)} />

{#if state.v3Scheduler}
    <EnumSelector
        label={tr.deckConfigInterdayStepPriority()}
        tooltip={tr.deckConfigInterdayStepPriorityTooltip()}
        choices={reviewMixChoices()}
        defaultValue={defaults.interdayLearningMix}
        bind:value={$config.interdayLearningMix} />
{/if}
