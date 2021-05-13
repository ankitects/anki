<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import SpinBox from "./SpinBox.svelte";
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

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];
</script>

<div>
    <h2>{tr.schedulingReviews()}</h2>

    <EnumSelector
        label={tr.deckConfigSortOrder()}
        tooltip={tr.deckConfigReviewSortOrderTooltip()}
        choices={reviewOrderChoices}
        defaultValue={defaults.reviewOrder}
        bind:value={$config.reviewOrder} />

    <SpinBox
        label={tr.schedulingLeechThreshold()}
        tooltip={tr.deckConfigLeechThresholdTooltip()}
        min={1}
        defaultValue={defaults.leechThreshold}
        bind:value={$config.leechThreshold} />

    <EnumSelector
        label={tr.schedulingLeechAction()}
        tooltip={tr.deckConfigLeechActionTooltip()}
        choices={leechChoices}
        defaultValue={defaults.leechAction}
        bind:value={$config.leechAction} />

    <CheckBox
        label={tr.deckConfigBuryReviewSiblings()}
        tooltip={tr.deckConfigBuryTooltip()}
        defaultValue={defaults.buryReviews}
        bind:value={$config.buryReviews} />
</div>
