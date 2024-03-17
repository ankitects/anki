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
    import FsrsOptions from "./FsrsOptions.svelte";
    import GlobalLabel from "./GlobalLabel.svelte";
    import type { DeckOptionsState } from "./lib";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const fsrs = state.fsrs;

    const settings = {
        fsrs: {
            title: "FSRS",
            help: tr.deckConfigFsrsTooltip(),
            url: HelpPage.DeckOptions.fsrs,
        },
        desiredRetention: {
            title: tr.deckConfigDesiredRetention(),
            help: tr.deckConfigDesiredRetentionTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        modelWeights: {
            title: tr.deckConfigWeights(),
            help:
                tr.deckConfigWeightsTooltip() +
                "\n\n" +
                tr.deckConfigComputeOptimalWeightsTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        ignoreRevlogsBeforeMs: {
            title: tr.deckConfigIgnoreBefore(),
            help: tr.deckConfigIgnoreBeforeTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        rescheduleCardsOnChange: {
            title: tr.deckConfigRescheduleCardsOnChange(),
            help: tr.deckConfigRescheduleCardsOnChangeTooltip(),
            sched: HelpItemScheduler.FSRS,
        },
        computeOptimalRetention: {
            title: tr.deckConfigComputeOptimalRetention(),
            help: tr.deckConfigComputeOptimalRetentionTooltip(),
            sched: HelpItemScheduler.FSRS,
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

<TitledContainer title={"FSRS"}>
    <HelpModal
        title={"FSRS"}
        url={HelpPage.DeckOptions.fsrs}
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
                    <GlobalLabel title={settings.fsrs.title} />
                </SettingTitle>
            </SwitchRow>
        </Item>

        <Warning warning={fsrsClientWarning} />

        {#if $fsrs}
            <FsrsOptions
                {state}
                openHelpModal={(key) =>
                    openHelpModal(Object.keys(settings).indexOf(key))}
            />
        {/if}
    </DynamicallySlottable>
</TitledContainer>
