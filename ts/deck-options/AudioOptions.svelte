<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOptionsState } from "./lib";
    import SwitchRow from "./SwitchRow.svelte";
    import TitledContainer from "./TitledContainer.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigAudioTitle()}>
    <DynamicallySlottable slotHost={Item} {api}>
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
                {tr.deckConfigSkipQuestionWhenReplaying()}
            </SwitchRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
