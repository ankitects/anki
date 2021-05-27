<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntryFull from "./ConfigEntryFull.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import CheckBox from "./CheckBox.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <ConfigEntryFull>
        <CheckBox defaultValue={defaults.buryNew} bind:value={$config.buryNew}>
            {tr.deckConfigBuryNewSiblings()}
            <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
        </CheckBox>
    </ConfigEntryFull>

    <ConfigEntryFull>
        <CheckBox defaultValue={defaults.buryReviews} bind:value={$config.buryReviews}>
            {tr.deckConfigBuryReviewSiblings()}
            <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
        </CheckBox>
    </ConfigEntryFull>
</TitledContainer>
