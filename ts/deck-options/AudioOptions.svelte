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
    import type { HelpItem } from "../components/types";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const settings = {
        disableAutoplay: {
            title: tr.deckConfigDisableAutoplay(),
            help: tr.deckConfigDisableAutoplayTooltip(),
        },
        skipQuestionWhenReplaying: {
            title: tr.deckConfigSkipQuestionWhenReplaying(),
            help: tr.deckConfigAlwaysIncludeQuestionAudioTooltip(),
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

<TitledContainer title={tr.deckConfigAudioTitle()}>
    <HelpModal
        title={tr.deckConfigAudioTitle()}
        url={HelpPage.DeckOptions.audio}
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <SwitchRow
                bind:value={$config.disableAutoplay}
                defaultValue={defaults.disableAutoplay}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("disableAutoplay"))}
                >
                    {settings.disableAutoplay.title}
                </SettingTitle>
            </SwitchRow>
        </Item>

        <Item>
            <SwitchRow
                bind:value={$config.skipQuestionWhenReplayingAnswer}
                defaultValue={defaults.skipQuestionWhenReplayingAnswer}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("skipQuestionWhenReplaying"),
                        )}
                >
                    {settings.skipQuestionWhenReplaying.title}
                </SettingTitle>
            </SwitchRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
