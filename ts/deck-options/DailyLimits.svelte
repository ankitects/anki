<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TitledContainer from "./TitledContainer.svelte";
    import Item from "components/Item.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Warning from "./Warning.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

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

<TitledContainer title={tr.deckConfigDailyLimits()} {api}>
    <Item>
        <SpinBoxRow
            bind:value={$config.newPerDay}
            defaultValue={defaults.newPerDay}
            markdownTooltip={tr.deckConfigNewLimitTooltip() + v3Extra}
        >
            {tr.schedulingNewCardsday()}
        </SpinBoxRow>

        <Warning warning={newCardsGreaterThanParent} />
    </Item>

    <Item>
        <SpinBoxRow
            bind:value={$config.reviewsPerDay}
            defaultValue={defaults.reviewsPerDay}
            markdownTooltip={tr.deckConfigReviewLimitTooltip() + v3Extra}
        >
            {tr.schedulingMaximumReviewsday()}
        </SpinBoxRow>

        <Warning warning={reviewsTooLow} />
    </Item>
</TitledContainer>
