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
    import { type HelpItem } from "$lib/components/types";
    import type { DeckOptionsState } from "./lib";
    import WeightsInputRow from "./WeightsInputRow.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrs = state.fsrs;

    const settings = {
        easyDays: {
            title: "Easy Days",
            help: "tr.deckConfigMaximumIntervalTooltip()",
            url: "",
        },
    };
    const helpSections = Object.values(settings) as HelpItem[];

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
        {#if $fsrs}
            <Item>
                <WeightsInputRow
                    bind:value={$config.easyDaysPercentages}
                    defaultValue={[]}
                    defaults={defaults.easyDaysPercentages}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("easyDaysPercentages"),
                            )}
                    >
                        {settings.easyDays.title}
                    </SettingTitle>
                </WeightsInputRow>
            </Item>
        {/if}
    </DynamicallySlottable>
</TitledContainer>
