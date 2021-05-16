<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import CheckBox from "./CheckBox.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;

    const reviewOrderChoices = [
        tr.deckConfigSortOrderDueDateThenRandom(),
        tr.deckConfigSortOrderAscendingIntervals(),
        tr.deckConfigSortOrderDescendingIntervals(),
    ];
</script>

<div>
    <h2>{tr.schedulingReviews()}</h2>

    {#if state.v3Scheduler}
        <EnumSelector
            label={tr.deckConfigSortOrder()}
            tooltip={tr.deckConfigReviewSortOrderTooltip()}
            choices={reviewOrderChoices}
            defaultValue={defaults.reviewOrder}
            bind:value={$config.reviewOrder} />
    {/if}

    <CheckBox
        label={tr.deckConfigBuryReviewSiblings()}
        tooltip={tr.deckConfigBuryTooltip()}
        defaultValue={defaults.buryReviews}
        bind:value={$config.buryReviews} />
</div>
