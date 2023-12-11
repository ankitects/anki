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
    import EnumSelectorRow from "../components/EnumSelectorRow.svelte";
    import HelpModal from "../components/HelpModal.svelte";
    import Item from "../components/Item.svelte";
    import SettingTitle from "../components/SettingTitle.svelte";
    import SwitchRow from "../components/SwitchRow.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import type { HelpItem } from "../components/types";
    import { answerChoices } from "./choices";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
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
        secondsToShowQuestion: {
            title: tr.deckConfigSecondsToShowQuestion(),
            help: tr.deckConfigSecondsToShowQuestionTooltip(),
        },
        secondsToShowAnswer: {
            title: tr.deckConfigSecondsToShowAnswer(),
            help: tr.deckConfigSecondsToShowAnswerTooltip(),
        },
        waitForAudio: {
            title: tr.deckConfigWaitForAudio(),
            help: tr.deckConfigWaitForAudioTooltip(),
        },
        answerAction: {
            title: tr.deckConfigAnswerAction(),
            help: tr.deckConfigAnswerActionTooltip(),
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

        <Item>
            <SpinBoxFloatRow
                bind:value={$config.secondsToShowQuestion}
                defaultValue={defaults.secondsToShowQuestion}
                step={0.1}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("secondsToShowQuestion"),
                        )}
                >
                    {settings.secondsToShowQuestion.title}
                </SettingTitle>
            </SpinBoxFloatRow>
        </Item>

        <Item>
            <SpinBoxFloatRow
                bind:value={$config.secondsToShowAnswer}
                defaultValue={defaults.secondsToShowAnswer}
                step={0.1}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("secondsToShowAnswer"),
                        )}
                >
                    {settings.secondsToShowAnswer.title}
                </SettingTitle>
            </SpinBoxFloatRow>
        </Item>

        <Item>
            <SwitchRow
                bind:value={$config.waitForAudio}
                defaultValue={defaults.waitForAudio}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("waitForAudio"))}
                >
                    {settings.waitForAudio.title}
                </SettingTitle>
            </SwitchRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.answerAction}
                defaultValue={defaults.answerAction}
                choices={answerChoices()}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("answerAction"))}
                >
                    {settings.answerAction.title}
                </SettingTitle>
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
