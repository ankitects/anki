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
    import CardStateCustomizer from "./CardStateCustomizer.svelte";
    import HelpModal from "./HelpModal.svelte";
    import type { DeckOptionsState } from "./lib";
    import SettingTitle from "./SettingTitle.svelte";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import type { DeckOption } from "./types";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const cardStateCustomizer = state.cardStateCustomizer;

    const settings = {
        maximumInterval: {
            title: tr.schedulingMaximumInterval(),
            help: tr.deckConfigMaximumIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#maximum-interval",
        },
        startingEase: {
            title: tr.schedulingStartingEase(),
            help: tr.deckConfigStartingEaseTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#starting-ease",
        },
        easyBonus: {
            title: tr.schedulingEasyBonus(),
            help: tr.deckConfigEasyBonusTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#easy-bonus",
        },
        intervalModifier: {
            title: tr.schedulingIntervalModifier(),
            help: tr.deckConfigIntervalModifierTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#interval-modifier",
        },
        hardInterval: {
            title: tr.schedulingHardInterval(),
            help: tr.deckConfigHardIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#hard-interval",
        },
        newInterval: {
            title: tr.schedulingNewInterval(),
            help: tr.deckConfigNewIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#new-interval",
        },
        customScheduling: {
            title: tr.deckConfigCustomScheduling(),
            help: tr.deckConfigCustomSchedulingTooltip(),
            url: "https://faqs.ankiweb.net/the-2021-scheduler.html#add-ons-and-custom-scheduling",
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

<TitledContainer title={tr.deckConfigAdvancedTitle()}>
    <HelpModal
        title={tr.deckConfigAdvancedTitle()}
        url="https://docs.ankiweb.net/deck-options.html#advanced"
        slot="tooltip"
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
                    >{settings.maximumInterval.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <SpinBoxFloatRow
                bind:value={$config.initialEase}
                defaultValue={defaults.initialEase}
                min={1.31}
                max={5}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("startingEase"))}
                    >{settings.startingEase.title}</SettingTitle
                >
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
                    >{settings.easyBonus.title}</SettingTitle
                >
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
                        )}>{settings.intervalModifier.title}</SettingTitle
                >
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
                        openHelpModal(Object.keys(settings).indexOf("hardInterval"))}
                    >{settings.hardInterval.title}</SettingTitle
                >
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
                    >{settings.newInterval.title}</SettingTitle
                >
            </SpinBoxFloatRow>
        </Item>

        {#if state.v3Scheduler}
            <Item>
                <CardStateCustomizer
                    title={settings.customScheduling.title}
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("customScheduling"),
                        )}
                    bind:value={$cardStateCustomizer}
                />
            </Item>
        {/if}
    </DynamicallySlottable>
</TitledContainer>
