<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { HelpPage } from "@tslib/help-page";
    import type Carousel from "bootstrap/js/dist/carousel";
    import type Modal from "bootstrap/js/dist/modal";

    import DynamicallySlottable from "../components/DynamicallySlottable.svelte";
    import HelpModal from "../components/HelpModal.svelte";
    import Item from "../components/Item.svelte";
    import SettingTitle from "../components/SettingTitle.svelte";
    import SwitchRow from "../components/SwitchRow.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import { type HelpItem, HelpItemScheduler } from "../components/types";
    import CardStateCustomizer from "./CardStateCustomizer.svelte";
    import FsrsOptions from "./FsrsOptions.svelte";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const cardStateCustomizer = state.cardStateCustomizer;
    const fsrs = state.fsrs;

    const settings = {
        fsrs: {
            title: tr.deckConfigAllDecks({ option: "FSRS" }),
            help: tr.deckConfigFsrsTooltip(),
            url: HelpPage.DeckOptions.fsrs,
        },
        maximumInterval: {
            title: tr.schedulingMaximumInterval(),
            help: tr.deckConfigMaximumIntervalTooltip(),
            url: HelpPage.DeckOptions.maximumInterval,
        },
        desiredRetention: {
            title: tr.deckConfigDesiredRetention(),
            help: tr.deckConfigDesiredRetentionTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        sm2Retention: {
            title: tr.deckConfigSm2Retention(),
            help: tr.deckConfigSm2RetentionTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        modelWeights: {
            title: tr.deckConfigWeights(),
            help: tr.deckConfigWeightsTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        rescheduleCardsOnChange: {
            title: tr.deckConfigRescheduleCardsOnChange(),
            help: tr.deckConfigRescheduleCardsOnChangeTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        computeOptimalWeights: {
            title: tr.deckConfigComputeOptimalWeights(),
            help: tr.deckConfigComputeOptimalWeightsTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        computeOptimalRetention: {
            title: tr.deckConfigComputeOptimalRetention(),
            help: tr.deckConfigComputeOptimalRetentionTooltip(),
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
            title: tr.deckConfigAllDecks({ option: tr.deckConfigCustomScheduling() }),
            help: tr.deckConfigCustomSchedulingTooltip(),
            url: "https://faqs.ankiweb.net/the-2021-scheduler.html#add-ons-and-custom-scheduling",
        },
    };
    const helpSections = Object.values(settings) as HelpItem[];

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(index: number): void {
        modal.show();
        carousel.to(index);
    }

    $: fsrsClientWarning = $fsrs ? tr.deckConfigFsrsOnAllClients() : "";
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
            <SwitchRow bind:value={$fsrs} defaultValue={false}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("fsrs"))}
                >
                    {settings.fsrs.title}
                </SettingTitle>
            </SwitchRow>
        </Item>

        <Warning warning={fsrsClientWarning} />

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
            <FsrsOptions
                {state}
                openHelpModal={(key) =>
                    openHelpModal(Object.keys(settings).indexOf(key))}
            />
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
