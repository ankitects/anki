<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
    import SpinBox from "./SpinBox.svelte";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;
    let config = state.currentConfig;
    let defaults = state.defaults;
    let parentLimits = state.parentLimits;

    $: newCardsGreaterThanParent =
        $config.newPerDay > $parentLimits.newCards
            ? `Daily limit will be capped to parent limit of ${$parentLimits.newCards}.`
            : "";

    // with the v2 scheduler, this no longer applies
    // $: reviewsGreaterThanParent =
    //     $config.reviewsPerDay > $parentLimits.reviews
    //         ? `Daily limit will be capped to parent limit of ${$parentLimits.reviews}.`
    //         : "";

    $: reviewsTooLow =
        $config.newPerDay * 10 > $config.reviewsPerDay
            ? `If adding ${
                  $config.newPerDay
              } new cards each day, your review limit should be at least ${
                  $config.newPerDay * 10
              }`
            : "";
</script>

<div>
    <h2>Daily Limits</h2>

    <SpinBox
        label={tr.schedulingNewCardsday()}
        subLabel="The maximum number of new cards to introduce in a day."
        min={0}
        warnings={[newCardsGreaterThanParent]}
        defaultValue={defaults.newPerDay}
        bind:value={$config.newPerDay} />

    <SpinBox
        label={tr.schedulingMaximumReviewsday()}
        subLabel="The maximum number of reviews cards to show in a day."
        min={0}
        warnings={[reviewsTooLow]}
        defaultValue={defaults.reviewsPerDay}
        bind:value={$config.reviewsPerDay} />
</div>
