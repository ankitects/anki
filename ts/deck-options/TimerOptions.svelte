<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import Item from "components/Item.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import SwitchRow from "./SwitchRow.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigTimerTitle()} {api}>
    <Item>
        <SpinBoxRow
            bind:value={$config.capAnswerTimeToSecs}
            defaultValue={defaults.capAnswerTimeToSecs}
            min={30}
            max={600}
            markdownTooltip={tr.deckConfigMaximumAnswerSecsTooltip()}
        >
            {tr.deckConfigMaximumAnswerSecs()}
        </SpinBoxRow>
    </Item>

    <Item>
        <SwitchRow
            bind:value={$config.showTimer}
            defaultValue={defaults.showTimer}
            markdownTooltip={tr.deckConfigShowAnswerTimerTooltip()}
        >
            {tr.schedulingShowAnswerTimer()}
        </SwitchRow>
    </Item>
</TitledContainer>
