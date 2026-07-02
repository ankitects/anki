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

    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const priorityTooltip = "\n\n" + tr.deckConfigBuryPriorityTooltip();

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
    const helpSections: HelpItem[] = Object.values(settings);

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
        url={HelpPage.Studying.siblingsAndBurying}
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
                >
                    {settings.buryNewSiblings.title}
                </SettingTitle>
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
                        )}
                >
                    {settings.buryReviewSiblings.title}
                </SettingTitle>
            </SwitchRow>
        </Item>

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
                >
                    {settings.buryInterdayLearningSiblings.title}
                </SettingTitle>
            </SwitchRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
