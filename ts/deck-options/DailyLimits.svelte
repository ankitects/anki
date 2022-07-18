<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import * as tr from "../lib/ftl";
    import type { DeckOptionsState } from "./lib";
    import { ValueTab } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import TabbedValue from "./TabbedValue.svelte";
    import TitledContainer from "./TitledContainer.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    export function onPresetChange() {
        newTabs[0] = new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.newPerDay,
            (value) => ($config.newPerDay = value!),
            $config.newPerDay,
            null,
        );
        reviewTabs[0] = new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.reviewsPerDay,
            (value) => ($config.reviewsPerDay = value!),
            $config.reviewsPerDay,
            null,
        );
    }

    const config = state.currentConfig;
    const limits = state.deckLimits;
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
        !state.v3Scheduler && newValue > $parentLimits.newCards
            ? tr.deckConfigDailyLimitWillBeCapped({ cards: $parentLimits.newCards })
            : "";

    $: reviewsTooLow =
        Math.min(9999, newValue * 10) > reviewsValue
            ? tr.deckConfigReviewsTooLow({
                  cards: newValue,
                  expected: Math.min(9999, newValue * 10),
              })
            : "";

    const newTabs: ValueTab[] = [
        new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.newPerDay,
            (value) => ($config.newPerDay = value!),
            $config.newPerDay,
            null,
        ),
        new ValueTab(
            tr.deckConfigDeckOnly(),
            $limits.new ?? null,
            (value) => ($limits.new = value),
            null,
            null,
        ),
        new ValueTab(
            tr.deckConfigTodayOnly(),
            $limits.newTodayActive ? $limits.newToday ?? null : null,
            (value) => ($limits.newToday = value),
            null,
            $limits.newToday ?? null,
        ),
    ];

    const reviewTabs: ValueTab[] = [
        new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.reviewsPerDay,
            (value) => ($config.reviewsPerDay = value!),
            $config.reviewsPerDay,
            null,
        ),
        new ValueTab(
            tr.deckConfigDeckOnly(),
            $limits.review ?? null,
            (value) => ($limits.review = value),
            null,
            null,
        ),
        new ValueTab(
            tr.deckConfigTodayOnly(),
            $limits.reviewTodayActive ? $limits.reviewToday ?? null : null,
            (value) => ($limits.reviewToday = value),
            null,
            $limits.reviewToday ?? null,
        ),
    ];

    let reviewsValue: number;
    let newValue: number;
</script>

<TitledContainer title={tr.deckConfigDailyLimits()}>
    <DynamicallySlottable slotHost={Item} {api}>
        <TabbedValue tabs={newTabs} bind:value={newValue} />
        <Item>
            <SpinBoxRow
                bind:value={newValue}
                defaultValue={defaults.newPerDay}
                markdownTooltip={tr.deckConfigNewLimitTooltip() + v3Extra}
            >
                {tr.schedulingNewCardsday()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={newCardsGreaterThanParent} />
        </Item>

        <TabbedValue tabs={reviewTabs} bind:value={reviewsValue} />
        <Item>
            <SpinBoxRow
                bind:value={reviewsValue}
                defaultValue={defaults.reviewsPerDay}
                markdownTooltip={tr.deckConfigReviewLimitTooltip() + v3Extra}
            >
                {tr.schedulingMaximumReviewsday()}
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={reviewsTooLow} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
