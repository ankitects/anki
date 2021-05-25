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

<h2>{tr.deckConfigBuryTitle()}</h2>

<CheckBox
    label={tr.deckConfigBuryNewSiblings()}
    tooltip={tr.deckConfigBuryTooltip()}
    defaultValue={defaults.buryNew}
    bind:value={$config.buryNew}
/>

<CheckBox
    label={tr.deckConfigBuryReviewSiblings()}
    tooltip={tr.deckConfigBuryTooltip()}
    defaultValue={defaults.buryReviews}
    bind:value={$config.buryReviews}
/>
