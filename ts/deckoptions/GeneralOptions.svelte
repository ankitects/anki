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

<div>
    <h2>General</h2>

    <SpinBox
        label={tr.schedulingIgnoreAnswerTimesLongerThan()}
        tooltip="The maximum number of seconds to record for a single review."
        min={30}
        max={600}
        defaultValue={defaults.capAnswerTimeToSecs}
        bind:value={$config.capAnswerTimeToSecs} />

    <CheckBox
        label={tr.schedulingShowAnswerTimer()}
        defaultValue={defaults.showTimer}
        bind:value={$config.showTimer} />

    <CheckBox
        label="Don't play audio automatically"
        defaultValue={defaults.disableAutoplay}
        bind:value={$config.disableAutoplay} />

    <CheckBox
        label={tr.schedulingAlwaysIncludeQuestionSideWhenReplaying()}
        defaultValue={defaults.skipQuestionWhenReplayingAnswer}
        bind:value={$config.skipQuestionWhenReplayingAnswer} />
</div>
