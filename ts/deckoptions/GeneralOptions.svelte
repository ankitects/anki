<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
    import CheckBox from "./CheckBox.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<h2>{tr.deckConfigTimerTitle()}</h2>

<SpinBox
    label={tr.deckConfigMaximumAnswerSecs()}
    tooltip={tr.deckConfigMaximumAnswerSecsTooltip()}
    min={30}
    max={600}
    defaultValue={defaults.capAnswerTimeToSecs}
    bind:value={$config.capAnswerTimeToSecs}
/>

<CheckBox
    id="showAnswerTimer"
    label={tr.schedulingShowAnswerTimer()}
    tooltip={tr.deckConfigShowAnswerTimerTooltip()}
    defaultValue={defaults.showTimer}
    bind:value={$config.showTimer}
/>

<h2>{tr.deckConfigAudioTitle()}</h2>

<CheckBox
    label={tr.deckConfigDisableAutoplay()}
    defaultValue={defaults.disableAutoplay}
    bind:value={$config.disableAutoplay}
/>

<CheckBox
    label={tr.schedulingAlwaysIncludeQuestionSideWhenReplaying()}
    tooltip={tr.deckConfigAlwaysIncludeQuestionAudioTooltip()}
    defaultValue={defaults.skipQuestionWhenReplayingAnswer}
    bind:value={$config.skipQuestionWhenReplayingAnswer}
/>
