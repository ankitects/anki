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
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    $: maximumAnswerSecondsAboveRecommended =
        $config.capAnswerTimeToSecs > 600
            ? tr.deckConfigMaximumAnswerSecsAboveRecommended()
            : "";

    const settings = {
        maximumAnswerSecs: {
            title: tr.deckConfigMaximumAnswerSecs(),
            help: tr.deckConfigMaximumAnswerSecsTooltip(),
        },
        showAnswerTimer: {
            title: tr.schedulingShowAnswerTimer(),
            help: tr.deckConfigShowAnswerTimerTooltip(),
        },
        stopTimerOnAnswer: {
            title: tr.deckConfigStopTimerOnAnswer(),
            help: tr.deckConfigStopTimerOnAnswerTooltip(),
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

<TitledContainer title={tr.deckConfigTimerTitle()}>
    <HelpModal
        title={tr.deckConfigTimerTitle()}
        url={HelpPage.DeckOptions.timer}
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
                bind:value={$config.capAnswerTimeToSecs}
                defaultValue={defaults.capAnswerTimeToSecs}
                min={1}
                max={7200}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("maximumAnswerSecs"),
                        )}
                >
                    {settings.maximumAnswerSecs.title}
                </SettingTitle>
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={maximumAnswerSecondsAboveRecommended} />
        </Item>

        <Item>
            <!-- AnkiMobile hides this -->
            <div class="show-timer-switch" style="display: contents;">
                <SwitchRow
                    bind:value={$config.showTimer}
                    defaultValue={defaults.showTimer}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("showAnswerTimer"),
                            )}
                    >
                        {settings.showAnswerTimer.title}
                    </SettingTitle>
                </SwitchRow>
            </div>
        </Item>

        <Item>
            <div class="show-timer-switch" style="display: contents;">
                <SwitchRow
                    bind:value={$config.stopTimerOnAnswer}
                    defaultValue={defaults.stopTimerOnAnswer}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("stopTimerOnAnswer"),
                            )}
                    >
                        {settings.stopTimerOnAnswer.title}
                    </SettingTitle>
                </SwitchRow>
            </div>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
