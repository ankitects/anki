<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Tabs from "./Tabs.svelte";
    import TitledContainer from "./TitledContainer.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const parentLimits = state.parentLimits;

    const v3Extra = state.v3Scheduler
        ? "\n\n" +
          tr.deckConfigLimitNewBoundByReviews() +
          "\n\n" +
          tr.deckConfigLimitInterdayBoundByReviews() +
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

    const tabs = ["Shared Preset", "Deck only", "Today only"];
    const activeReviewTab = 0;
    const activeNewTab = 0;
</script>

<TitledContainer title={tr.deckConfigDailyLimits()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <Tabs {tabs} activeTab={activeReviewTab}>
            <Item>
                <SpinBoxRow
                    bind:value={$config.newPerDay}
                    defaultValue={defaults.newPerDay}
                    markdownTooltip={tr.deckConfigNewLimitTooltip() + v3Extra}
                >
                    {tr.schedulingNewCardsday()}
                </SpinBoxRow>
            </Item></Tabs
        >

        <Item>
            <Warning warning={newCardsGreaterThanParent} />
        </Item>

        <Tabs {tabs} activeTab={activeNewTab}>
            <Item>
                <SpinBoxRow
                    bind:value={$config.reviewsPerDay}
                    defaultValue={defaults.reviewsPerDay}
                    markdownTooltip={tr.deckConfigReviewLimitTooltip() + v3Extra}
                >
                    {tr.schedulingMaximumReviewsday()}
                </SpinBoxRow>
            </Item></Tabs
        >

        <Item>
            <Warning warning={reviewsTooLow} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
