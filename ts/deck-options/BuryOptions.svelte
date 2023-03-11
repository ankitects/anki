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
    import SettingTitle from "./SettingTitle.svelte";
    import SwitchRow from "./SwitchRow.svelte";
    import type { DeckOption } from "./types";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const priorityTooltip = state.v3Scheduler
        ? "\n\n" + tr.deckConfigBuryPriorityTooltip()
        : "";

    const settings = {
        buryNewSiblings: {
            title: tr.deckConfigBuryNewSiblings(),
            help: tr.deckConfigBuryNewTooltip() + priorityTooltip,
        },
        buryReviewSiblings: {
            title: tr.deckConfigBuryReviewSiblings(),
            help: tr.deckConfigBuryReviewTooltip() + priorityTooltip,
        },
        buryInterdayLearningSiblings: {
            title: tr.deckConfigBuryInterdayLearningSiblings(),
            help: tr.deckConfigBuryInterdayLearningTooltip() + priorityTooltip,
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

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <HelpModal
        title={tr.deckConfigBuryTitle()}
        url="https://docs.ankiweb.net/studying.html#siblings-and-burying"
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SwitchRow bind:value={$config.buryNew} defaultValue={defaults.buryNew}>
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("buryNewSiblings"))}
                    >{settings.buryNewSiblings.title}</SettingTitle
                >
            </SwitchRow>
        </Item>

        <Item>
            <SwitchRow
                bind:value={$config.buryReviews}
                defaultValue={defaults.buryReviews}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("buryReviewSiblings"),
                        )}>{settings.buryReviewSiblings.title}</SettingTitle
                >
            </SwitchRow>
        </Item>

        {#if state.v3Scheduler}
            <Item>
                <SwitchRow
                    bind:value={$config.buryInterdayLearning}
                    defaultValue={defaults.buryInterdayLearning}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf(
                                    "buryInterdayLearningSiblings",
                                ),
                            )}
                        >{settings.buryInterdayLearningSiblings.title}</SettingTitle
                    >
                </SwitchRow>
            </Item>
        {/if}
    </DynamicallySlottable>
</TitledContainer>
