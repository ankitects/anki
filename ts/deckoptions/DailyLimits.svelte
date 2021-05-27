<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import Warnings from "./Warnings.svelte";
    import SpinBox from "./SpinBox.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
    let parentLimits = state.parentLimits;

    const v3Extra = state.v3Scheduler
        ? "\n\n" +
          tr.deckConfigLimitNewBoundByReviews() +
          "\n\n" +
          tr.deckConfigLimitDeckV3()
        : "";

    $: newCardsGreaterThanParent =
        !state.v3Scheduler && $config.newPerDay > $parentLimits.newCards
            ? tr.deckConfigDailyLimitWillBeCapped({ cards: $parentLimits.newCards })
            : "";

    $: reviewsTooLow =
        Math.min(9999, $config.newPerDay * 10) > $config.reviewsPerDay
            ? tr.deckConfigReviewsTooLow({
                  cards: $config.newPerDay,
                  expected: Math.min(9999, $config.newPerDay * 10),
              })
            : "";
</script>

<TitledContainer title={tr.deckConfigDailyLimits()}>
    <ConfigEntry>
        <span slot="left">
            {tr.schedulingNewCardsday()}<HelpPopup
                html={marked(tr.deckConfigNewLimitTooltip() + v3Extra)}
            />
        </span>
        <svelte:fragment slot="right">
            <SpinBox
                min={0}
                defaultValue={defaults.newPerDay}
                bind:value={$config.newPerDay}
            />
        </svelte:fragment>
    </ConfigEntry>

    <Warnings warnings={[newCardsGreaterThanParent]} />

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingMaximumReviewsday()}<HelpPopup
                html={marked(tr.deckConfigReviewLimitTooltip() + v3Extra)}
            />
        </span>
        <svelte:fragment slot="right">
            <SpinBox
                min={0}
                defaultValue={defaults.reviewsPerDay}
                bind:value={$config.reviewsPerDay}
            />
        </svelte:fragment>
    </ConfigEntry>

    <Warnings warnings={[reviewsTooLow]} />
</TitledContainer>
