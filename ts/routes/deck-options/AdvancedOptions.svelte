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
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import { type HelpItem, HelpItemScheduler } from "$lib/components/types";

    import CardStateCustomizer from "./CardStateCustomizer.svelte";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import DateInput from "./DateInput.svelte";
    import Warning from "./Warning.svelte";
    import { getIgnoredBeforeCount } from "@generated/backend";
    import type { GetIgnoredBeforeCountResponse } from "@generated/anki/deck_config_pb";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const cardStateCustomizer = state.cardStateCustomizer;
    const fsrs = state.fsrs;

    const settings = {
        maximumInterval: {
            title: tr.schedulingMaximumInterval(),
            help: tr.deckConfigMaximumIntervalTooltip(),
            url: HelpPage.DeckOptions.maximumInterval,
        },
        historicalRetention: {
            title: tr.deckConfigHistoricalRetention(),
            help: tr.deckConfigHistoricalRetentionTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        ignoreRevlogsBeforeMs: {
            title: tr.deckConfigIgnoreBefore(),
            help: tr.deckConfigIgnoreBeforeTooltip2(),
            sched: HelpItemScheduler.FSRS,
        },
        startingEase: {
            title: tr.schedulingStartingEase(),
            help: tr.deckConfigStartingEaseTooltip(),
            url: HelpPage.DeckOptions.startingEase,
            sched: HelpItemScheduler.SM2,
        },
        easyBonus: {
            title: tr.schedulingEasyBonus(),
            help: tr.deckConfigEasyBonusTooltip(),
            url: HelpPage.DeckOptions.easyBonus,
            sched: HelpItemScheduler.SM2,
        },
        intervalModifier: {
            title: tr.schedulingIntervalModifier(),
            help: tr.deckConfigIntervalModifierTooltip(),
            url: HelpPage.DeckOptions.intervalModifier,
            sched: HelpItemScheduler.SM2,
        },
        hardInterval: {
            title: tr.schedulingHardInterval(),
            help: tr.deckConfigHardIntervalTooltip(),
            url: HelpPage.DeckOptions.hardInterval,
            sched: HelpItemScheduler.SM2,
        },
        newInterval: {
            title: tr.schedulingNewInterval(),
            help: tr.deckConfigNewIntervalTooltip(),
            url: HelpPage.DeckOptions.newInterval,
            sched: HelpItemScheduler.SM2,
        },
        customScheduling: {
            title: tr.deckConfigCustomScheduling(),
            help: tr.deckConfigCustomSchedulingTooltip(),
            url: "https://faqs.ankiweb.net/the-2021-scheduler.html#add-ons-and-custom-scheduling",
        },
    };
    const helpSections: HelpItem[] = Object.values(settings);

    $: maxIntervalWarningClass =
        $config.maximumReviewInterval < 50 ? "alert-danger" : "alert-warning";
    $: maxIntervalWarning =
        $config.maximumReviewInterval < 180
            ? tr.deckConfigTooShortMaximumInterval()
            : "";

    $: if ($config.ignoreRevlogsBeforeDate != "1970-01-01") {
        getIgnoredBeforeCount({
            search:
                $config.paramSearch ||
                `preset:"${state.getCurrentNameForSearch()}" -is:suspended`,
            ignoreRevlogsBeforeDate: $config.ignoreRevlogsBeforeDate,
        }).then((resp) => {
            ignoreRevlogsBeforeCount = resp;
        });
    }
    let ignoreRevlogsBeforeCount: GetIgnoredBeforeCountResponse | null = null;
    let ignoreRevlogsBeforeWarningClass = "alert-warning";
    $: if (ignoreRevlogsBeforeCount) {
        // If there is less than a tenth of reviews included
        console.log(ignoreRevlogsBeforeCount.included, ignoreRevlogsBeforeCount.total )
        if (Number(ignoreRevlogsBeforeCount.included) / Number(ignoreRevlogsBeforeCount.total) < 0.1) {
            ignoreRevlogsBeforeWarningClass = "alert-danger";
        } else if (
            ignoreRevlogsBeforeCount.included != ignoreRevlogsBeforeCount.total
        ) {
            ignoreRevlogsBeforeWarningClass = "alert-warning";
        } else {
            ignoreRevlogsBeforeWarningClass = "alert-info";
        }
    }
    $: ignoreRevlogsBeforeWarning = ignoreRevlogsBeforeCount
        ? tr.deckConfigIgnoreBeforeInfo({included: ignoreRevlogsBeforeCount.included.toString(), totalCards: ignoreRevlogsBeforeCount.total.toString()})
        : "";

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }
</script>

<TitledContainer title={tr.deckConfigAdvancedTitle()}>
    <HelpModal
        title={tr.deckConfigAdvancedTitle()}
        url={HelpPage.DeckOptions.advanced}
        slot="tooltip"
        fsrs={$fsrs}
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SpinBoxRow
                bind:value={$config.maximumReviewInterval}
                defaultValue={defaults.maximumReviewInterval}
                min={1}
                max={365 * 100}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("maximumInterval"))}
                >
                    {settings.maximumInterval.title}
                </SettingTitle>
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={maxIntervalWarning} className={maxIntervalWarningClass}
            ></Warning>
        </Item>

        {#if !$fsrs}
            <Item>
                <SpinBoxFloatRow
                    bind:value={$config.initialEase}
                    defaultValue={defaults.initialEase}
                    min={1.31}
                    max={5}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("startingEase"),
                            )}
                    >
                        {settings.startingEase.title}
                    </SettingTitle>
                </SpinBoxFloatRow>
            </Item>

            <Item>
                <SpinBoxFloatRow
                    bind:value={$config.easyMultiplier}
                    defaultValue={defaults.easyMultiplier}
                    min={1}
                    max={5}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(Object.keys(settings).indexOf("easyBonus"))}
                    >
                        {settings.easyBonus.title}
                    </SettingTitle>
                </SpinBoxFloatRow>
            </Item>

            <Item>
                <SpinBoxFloatRow
                    bind:value={$config.intervalMultiplier}
                    defaultValue={defaults.intervalMultiplier}
                    min={0.5}
                    max={2}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("intervalModifier"),
                            )}
                    >
                        {settings.intervalModifier.title}
                    </SettingTitle>
                </SpinBoxFloatRow>
            </Item>

            <Item>
                <SpinBoxFloatRow
                    bind:value={$config.hardMultiplier}
                    defaultValue={defaults.hardMultiplier}
                    min={0.5}
                    max={1.3}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("hardInterval"),
                            )}
                    >
                        {settings.hardInterval.title}
                    </SettingTitle>
                </SpinBoxFloatRow>
            </Item>

            <Item>
                <SpinBoxFloatRow
                    bind:value={$config.lapseMultiplier}
                    defaultValue={defaults.lapseMultiplier}
                    max={1}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(Object.keys(settings).indexOf("newInterval"))}
                    >
                        {settings.newInterval.title}
                    </SettingTitle>
                </SpinBoxFloatRow>
            </Item>
        {:else}
            <SpinBoxFloatRow
                bind:value={$config.historicalRetention}
                defaultValue={defaults.historicalRetention}
                min={0.5}
                max={1.0}
                percentage={true}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("historicalRetention"),
                        )}
                >
                    {tr.deckConfigHistoricalRetention()}
                </SettingTitle>
            </SpinBoxFloatRow>

            <Item>
                <DateInput bind:date={$config.ignoreRevlogsBeforeDate}>
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("ignoreRevlogsBeforeMs"),
                            )}
                    >
                        {tr.deckConfigIgnoreBefore()}
                    </SettingTitle>
                </DateInput>
            </Item>

            <Item>
                <Warning
                    warning={ignoreRevlogsBeforeWarning}
                    className={ignoreRevlogsBeforeWarningClass}
                ></Warning>
            </Item>
        {/if}

        <Item>
            <CardStateCustomizer
                title={settings.customScheduling.title}
                on:click={() =>
                    openHelpModal(Object.keys(settings).indexOf("customScheduling"))}
                bind:value={$cardStateCustomizer}
            />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
