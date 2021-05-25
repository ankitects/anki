<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<h2>{tr.deckConfigAdvancedTitle()}</h2>

<SpinBox
    label={tr.schedulingMaximumInterval()}
    tooltip={tr.deckConfigMaximumIntervalTooltip()}
    min={1}
    max={365 * 100}
    defaultValue={defaults.maximumReviewInterval}
    bind:value={$config.maximumReviewInterval}
/>

<SpinBoxFloat
    label={tr.schedulingStartingEase()}
    tooltip={tr.deckConfigStartingEaseTooltip()}
    min={1.31}
    max={5}
    defaultValue={defaults.initialEase}
    value={$config.initialEase}
    on:changed={(evt) => ($config.initialEase = evt.detail.value)}
/>

<SpinBoxFloat
    label={tr.schedulingEasyBonus()}
    tooltip={tr.deckConfigEasyBonusTooltip()}
    min={1}
    max={3}
    defaultValue={defaults.easyMultiplier}
    value={$config.easyMultiplier}
    on:changed={(evt) => ($config.easyMultiplier = evt.detail.value)}
/>

<SpinBoxFloat
    label={tr.schedulingIntervalModifier()}
    tooltip={tr.deckConfigIntervalModifierTooltip()}
    min={0.5}
    max={2}
    defaultValue={defaults.intervalMultiplier}
    value={$config.intervalMultiplier}
    on:changed={(evt) => ($config.intervalMultiplier = evt.detail.value)}
/>

<SpinBoxFloat
    label={tr.schedulingHardInterval()}
    tooltip={tr.deckConfigHardIntervalTooltip()}
    min={0.5}
    max={1.3}
    defaultValue={defaults.hardMultiplier}
    value={$config.hardMultiplier}
    on:changed={(evt) => ($config.hardMultiplier = evt.detail.value)}
/>

<SpinBoxFloat
    label={tr.schedulingNewInterval()}
    tooltip={tr.deckConfigNewIntervalTooltip()}
    min={0}
    max={1}
    defaultValue={defaults.lapseMultiplier}
    value={$config.lapseMultiplier}
    on:changed={(evt) => ($config.lapseMultiplier = evt.detail.value)}
/>
