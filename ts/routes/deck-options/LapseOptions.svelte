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
    import EnumSelectorRow from "$lib/components/EnumSelectorRow.svelte";
    import HelpModal from "$lib/components/HelpModal.svelte";
    import Item from "$lib/components/Item.svelte";
    import SettingTitle from "$lib/components/SettingTitle.svelte";
    import TitledContainer from "$lib/components/TitledContainer.svelte";
    import { type HelpItem, HelpItemScheduler } from "$lib/components/types";

    import { leechChoices } from "./choices";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import StepsInputRow from "./StepsInputRow.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api = {};

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrs = state.fsrs;

    let stepsExceedMinimumInterval: string;
    let stepsTooLargeForFsrs: string;
    $: {
        const lastRelearnStepInDays = $config.relearnSteps.length
            ? $config.relearnSteps[$config.relearnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedMinimumInterval =
            !$fsrs && lastRelearnStepInDays > $config.minimumLapseInterval
                ? tr.deckConfigRelearningStepsAboveMinimumInterval()
                : "";
        stepsTooLargeForFsrs =
            $fsrs && lastRelearnStepInDays >= 1
                ? tr.deckConfigStepsTooLargeForFsrs()
                : "";
    }

    const settings = {
        relearningSteps: {
            title: tr.deckConfigRelearningSteps(),
            help: tr.deckConfigRelearningStepsTooltip(),
            url: HelpPage.DeckOptions.relearningSteps,
        },
        minimumInterval: {
            title: tr.schedulingMinimumInterval(),
            help: tr.deckConfigMinimumIntervalTooltip(),
            url: HelpPage.DeckOptions.minimumInterval,
            sched: HelpItemScheduler.SM2,
        },
        leechThreshold: {
            title: tr.schedulingLeechThreshold(),
            help: tr.deckConfigLeechThresholdTooltip(),
            url: HelpPage.Leeches.leeches,
        },
        leechAction: {
            title: tr.schedulingLeechAction(),
            help: tr.deckConfigLeechActionTooltip(),
            url: HelpPage.Leeches.waiting,
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

<TitledContainer title={tr.schedulingLapses()}>
    <HelpModal
        title={tr.schedulingLapses()}
        url={HelpPage.DeckOptions.lapses}
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
            <StepsInputRow
                bind:value={$config.relearnSteps}
                defaultValue={defaults.relearnSteps}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("relearningSteps"))}
                >
                    {settings.relearningSteps.title}
                </SettingTitle>
            </StepsInputRow>
        </Item>

        <Item>
            <Warning warning={stepsTooLargeForFsrs} />
        </Item>

        {#if !$fsrs}
            <Item>
                <SpinBoxRow
                    bind:value={$config.minimumLapseInterval}
                    defaultValue={defaults.minimumLapseInterval}
                    min={1}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("minimumInterval"),
                            )}
                    >
                        {settings.minimumInterval.title}
                    </SettingTitle>
                </SpinBoxRow>
            </Item>
        {/if}

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
                >
                    {settings.leechThreshold.title}
                </SettingTitle>
            </SpinBoxRow>
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.leechAction}
                defaultValue={defaults.leechAction}
                choices={leechChoices()}
                breakpoint="md"
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("leechAction"))}
                >
                    {settings.leechAction.title}
                </SettingTitle>
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
