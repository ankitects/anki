<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import Item from "components/Item.svelte";
    import SwitchRow from "./SwitchRow.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigAudioTitle()} {api}>
    <Item>
        <SwitchRow
            bind:value={$config.disableAutoplay}
            defaultValue={defaults.disableAutoplay}
        >
            {tr.deckConfigDisableAutoplay()}
        </SwitchRow>
    </Item>

    <Item>
        <SwitchRow
            bind:value={$config.skipQuestionWhenReplayingAnswer}
            defaultValue={defaults.skipQuestionWhenReplayingAnswer}
            markdownTooltip={tr.deckConfigAlwaysIncludeQuestionAudioTooltip()}
        >
            {tr.schedulingAlwaysIncludeQuestionSideWhenReplaying()}
        </SwitchRow>
    </Item>
</TitledContainer>
