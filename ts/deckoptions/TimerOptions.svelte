<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import ConfigEntryFull from "./ConfigEntryFull.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import SpinBox from "./SpinBox.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigTimerTitle()}>
    <ConfigEntry>
        <span slot="left">
            {tr.deckConfigMaximumAnswerSecs()}
            <HelpPopup html={marked(tr.deckConfigMaximumAnswerSecsTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBox min={30} max={600} bind:value={$config.capAnswerTimeToSecs} />
            <RevertButton
                defaultValue={defaults.capAnswerTimeToSecs}
                bind:value={$config.capAnswerTimeToSecs}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntryFull>
        <CheckBox bind:value={$config.showTimer}>
            {tr.schedulingShowAnswerTimer()}
            <HelpPopup html={marked(tr.deckConfigShowAnswerTimerTooltip())} />
        </CheckBox>
        <RevertButton
            defaultValue={defaults.showTimer}
            bind:value={$config.showTimer}
        />
    </ConfigEntryFull>
</TitledContainer>
