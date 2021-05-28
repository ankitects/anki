<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <ConfigEntry>
        <svelte:fragment slot="left">
            <CheckBox bind:value={$config.buryNew}>
                {tr.deckConfigBuryNewSiblings()}
                <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
            </CheckBox>
        </svelte:fragment>
        <svelte:fragment slot="right">
            <RevertButton
                defaultValue={defaults.buryNew}
                bind:value={$config.buryNew}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <svelte:fragment slot="left">
            <CheckBox bind:value={$config.buryReviews}>
                {tr.deckConfigBuryReviewSiblings()}
                <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
            </CheckBox>
        </svelte:fragment>
        <svelte:fragment slot="right">
            <RevertButton
                defaultValue={defaults.buryReviews}
                bind:value={$config.buryReviews}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>
