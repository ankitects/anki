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
    import EnumSelectorRow from "./EnumSelectorRow.svelte";
    import HelpModal from "./HelpModal.svelte";
    import type { DeckOptionsState } from "./lib";
    import SettingTitle from "./SettingTitle.svelte";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import StepsInputRow from "./StepsInputRow.svelte";
    import type { DeckOption } from "./types";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api = {};

    const config = state.currentConfig;
    const defaults = state.defaults;

    let stepsExceedMinimumInterval: string;
    $: {
        const lastRelearnStepInDays = $config.relearnSteps.length
            ? $config.relearnSteps[$config.relearnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedMinimumInterval =
            lastRelearnStepInDays > $config.minimumLapseInterval
                ? tr.deckConfigRelearningStepsAboveMinimumInterval()
                : "";
    }

    const leechChoices = [tr.actionsSuspendCard(), tr.schedulingTagOnly()];

    const settings = {
        relearningSteps: {
            title: tr.deckConfigRelearningSteps(),
            help: tr.deckConfigRelearningStepsTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#relearning-steps",
        },
        minimumInterval: {
            title: tr.schedulingMinimumInterval(),
            help: tr.deckConfigMinimumIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#minimum-interval",
        },
        leechThreshold: {
            title: tr.schedulingLeechThreshold(),
            help: tr.deckConfigLeechThresholdTooltip(),
            url: "https://docs.ankiweb.net/leeches.html#leeches",
        },
        leechAction: {
            title: tr.schedulingLeechAction(),
            help: tr.deckConfigLeechActionTooltip(),
            url: "https://docs.ankiweb.net/leeches.html#waiting",
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

<TitledContainer title={tr.schedulingLapses()}>
    <HelpModal
        title={tr.schedulingLapses()}
        url="https://docs.ankiweb.net/deck-options.html#lapses"
        slot="tooltip"
        {helpSections}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <StepsInputRow
                bind:value={$config.relearnSteps}
                defaultValue={defaults.relearnSteps}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("relearningSteps"))}
                    >{settings.relearningSteps.title}</SettingTitle
                >
            </StepsInputRow>
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.minimumLapseInterval}
                defaultValue={defaults.minimumLapseInterval}
                min={1}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("minimumInterval"))}
                    >{settings.minimumInterval.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={stepsExceedMinimumInterval} />
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.leechThreshold}
                defaultValue={defaults.leechThreshold}
                min={1}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("leechThreshold"))}
                    >{settings.leechThreshold.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.leechAction}
                defaultValue={defaults.leechAction}
                choices={leechChoices}
                breakpoint="md"
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("leechAction"))}
                    >{settings.leechAction.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
