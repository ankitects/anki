<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import CheckBox from "./CheckBox.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

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
