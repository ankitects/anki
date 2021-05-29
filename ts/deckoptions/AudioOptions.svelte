<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import CheckBoxRow from "./CheckBoxRow.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigAudioTitle()}>
    <CheckBoxRow
        bind:value={$config.disableAutoplay}
        defaultValue={defaults.disableAutoplay}
    >
        {tr.deckConfigDisableAutoplay()}
    </CheckBoxRow>

    <CheckBoxRow
        bind:value={$config.skipQuestionWhenReplayingAnswer}
        defaultValue={defaults.skipQuestionWhenReplayingAnswer}
        markdownTooltip={tr.deckConfigAlwaysIncludeQuestionAudioTooltip()}
    >
        {tr.schedulingAlwaysIncludeQuestionSideWhenReplaying()}
    </CheckBoxRow>
</TitledContainer>
