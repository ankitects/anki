<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import Item from "../components/Item.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import HelpModal from "./HelpModal.svelte";
    import type { DeckOptionsState } from "./lib";
    import { ValueTab } from "./lib";
    import SettingTitle from "./SettingTitle.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import SwitchRow from "./SwitchRow.svelte";
    import TabbedValue from "./TabbedValue.svelte";
    import type { DeckOption } from "./types";
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
    const newCardsIgnoreReviewLimit = state.newCardsIgnoreReviewLimit;

    const v3Extra = state.v3Scheduler
        ? "\n\n" + tr.deckConfigLimitDeckV3() + "\n\n" + tr.deckConfigTabDescription()
        : "";
    const reviewV3Extra = state.v3Scheduler
        ? "\n\n" + tr.deckConfigLimitInterdayBoundByReviews() + v3Extra
        : "";
    const newCardsIgnoreReviewLimitHelp =
        tr.deckConfigAffectsEntireCollection() +
        "\n\n" +
        tr.deckConfigNewCardsIgnoreReviewLimitTooltip();

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
    ].concat(
        state.v3Scheduler
            ? [
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
              ]
            : [],
    );

    const reviewTabs: ValueTab[] = [
        new ValueTab(
            tr.deckConfigSharedPreset(),
            $config.reviewsPerDay,
            (value) => ($config.reviewsPerDay = value!),
            $config.reviewsPerDay,
            null,
        ),
    ].concat(
        state.v3Scheduler
            ? [
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
              ]
            : [],
    );

    let newValue = 0;
    let reviewsValue = 0;

    const settings = {
        newLimit: {
            title: tr.schedulingNewCardsday(),
            help: tr.deckConfigNewLimitTooltip() + v3Extra,
            url: "https://docs.ankiweb.net/deck-options.html#new-cardsday",
        },
        reviewLimit: {
            title: tr.schedulingMaximumReviewsday(),
            help: tr.deckConfigReviewLimitTooltip() + reviewV3Extra,
            url: "https://docs.ankiweb.net/deck-options.html#maximum-reviewsday",
        },
        newCardsIgnoreReviewLimit: {
            title: tr.deckConfigNewCardsIgnoreReviewLimit(),
            help: newCardsIgnoreReviewLimitHelp,
            url: "https://docs.ankiweb.net/deck-options.html#new-cardsday",
        },
    };
    const helpSections = Object.values(settings) as DeckOption[];

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.deckConfigDailyLimits()}>
    <HelpModal
        title={tr.deckConfigDailyLimits()}
        url="https://docs.ankiweb.net/deck-options.html#daily-limits"
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SpinBoxRow bind:value={newValue} defaultValue={defaults.newPerDay}>
                <TabbedValue slot="tabs" tabs={newTabs} bind:value={newValue} />
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("newLimit"))}
                    >{settings.newLimit.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={newCardsGreaterThanParent} />
        </Item>
        <Item>
            <SpinBoxRow bind:value={reviewsValue} defaultValue={defaults.reviewsPerDay}>
                <TabbedValue slot="tabs" tabs={reviewTabs} bind:value={reviewsValue} />
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("reviewLimit"))}
                    >{settings.reviewLimit.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={reviewsTooLow} />
        </Item>

        {#if state.v3Scheduler}
            <Item>
                <SwitchRow bind:value={$newCardsIgnoreReviewLimit} defaultValue={false}>
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("newIgnoreReviewLimit"),
                            )}>{settings.newCardsIgnoreReviewLimit.title}</SettingTitle
                    >
                </SwitchRow>
            </Item>
        {/if}
    </DynamicallySlottable>
</TitledContainer>
