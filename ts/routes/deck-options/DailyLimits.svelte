<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import Item from "$lib/components/Item.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import SwitchRow from "$lib/components/SwitchRow.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import type { HelpItem } from "$lib/components/types";

    import GlobalLabel from "./GlobalLabel.svelte";
    import type { DeckOptionsState } from "./lib";
    import { ValueTab } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import TabbedValue from "./TabbedValue.svelte";
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
    const newCardsIgnoreReviewLimit = state.newCardsIgnoreReviewLimit;
    const applyAllParentLimits = state.applyAllParentLimits;

    const v3Extra =
        "\n\n" + tr.deckConfigLimitDeckV3() + "\n\n" + tr.deckConfigTabDescription();
    const reviewV3Extra = "\n\n" + tr.deckConfigLimitInterdayBoundByReviews() + v3Extra;
    const newCardsIgnoreReviewLimitHelp =
        tr.deckConfigAffectsEntireCollection() +
        "\n\n" +
        tr.deckConfigNewCardsIgnoreReviewLimitTooltip();
    const applyAllParentLimitsHelp =
        tr.deckConfigAffectsEntireCollection() +
        "\n\n" +
        tr.deckConfigApplyAllParentLimitsTooltip();

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
            (value) => ($limits.new = value ?? undefined),
            null,
            null,
        ),
        new ValueTab(
            tr.deckConfigTodayOnly(),
            $limits.newTodayActive ? ($limits.newToday ?? null) : null,
            (value) => ($limits.newToday = value ?? undefined),
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
            (value) => ($limits.review = value ?? undefined),
            null,
            null,
        ),
        new ValueTab(
            tr.deckConfigTodayOnly(),
            $limits.reviewTodayActive ? ($limits.reviewToday ?? null) : null,
            (value) => ($limits.reviewToday = value ?? undefined),
            null,
            $limits.reviewToday ?? null,
        ),
    ];

    let newValue = 0;
    let reviewsValue = 0;

    const settings = {
        newLimit: {
            title: tr.schedulingNewCardsday(),
            help: tr.deckConfigNewLimitTooltip() + v3Extra,
            url: HelpPage.DeckOptions.newCardsday,
        },
        reviewLimit: {
            title: tr.schedulingMaximumReviewsday(),
            help: tr.deckConfigReviewLimitTooltip() + reviewV3Extra,
            url: HelpPage.DeckOptions.maximumReviewsday,
        },
        newCardsIgnoreReviewLimit: {
            title: tr.deckConfigNewCardsIgnoreReviewLimit(),

            help: newCardsIgnoreReviewLimitHelp,
            url: HelpPage.DeckOptions.newCardsday,
        },
        applyAllParentLimits: {
            title: tr.deckConfigApplyAllParentLimits(),
            help: applyAllParentLimitsHelp,
            url: HelpPage.DeckOptions.newCardsday,
        },
    };
    const helpSections: HelpItem[] = Object.values(settings);

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
        url={HelpPage.DeckOptions.dailyLimits}
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
                >
                    {settings.newLimit.title}
                </SettingTitle>
            </SpinBoxRow>
        </Item>

        <Item>
            <SpinBoxRow bind:value={reviewsValue} defaultValue={defaults.reviewsPerDay}>
                <TabbedValue slot="tabs" tabs={reviewTabs} bind:value={reviewsValue} />
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("reviewLimit"))}
                >
                    {settings.reviewLimit.title}
                </SettingTitle>
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={reviewsTooLow} />
        </Item>

        <Item>
            <SwitchRow bind:value={$newCardsIgnoreReviewLimit} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("newCardsIgnoreReviewLimit"),
                        )}
                >
                    <GlobalLabel title={settings.newCardsIgnoreReviewLimit.title} />
                </SettingTitle>
            </SwitchRow>
        </Item>

        <Item>
            <SwitchRow bind:value={$applyAllParentLimits} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("applyAllParentLimits"),
                        )}
                >
                    <GlobalLabel title={settings.applyAllParentLimits.title} />
                </SettingTitle>
            </SwitchRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
