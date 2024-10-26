<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { DeckConfig_Config_NewCardInsertOrder } from "@generated/anki/deck_config_pb";
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

    import { newInsertOrderChoices } from "./choices";
    import type { DeckOptionsState } from "./lib";
    import SpinBoxRow from "./SpinBoxRow.svelte";
    import StepsInputRow from "./StepsInputRow.svelte";
    import Warning from "./Warning.svelte";

    export let state: DeckOptionsState;
    export let api = {};

    const config = state.currentConfig;
    const defaults = state.defaults;
    const fsrs = state.fsrs;

    let stepsExceedGraduatingInterval: string;
    let stepsTooLargeForFsrs: string;
    $: {
        const lastLearnStepInDays = $config.learnSteps.length
            ? $config.learnSteps[$config.learnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedGraduatingInterval =
            lastLearnStepInDays > $config.graduatingIntervalGood
                ? tr.deckConfigLearningStepAboveGraduatingInterval()
                : "";
        stepsTooLargeForFsrs =
            $fsrs && lastLearnStepInDays >= 1
                ? tr.deckConfigStepsTooLargeForFsrs()
                : "";
    }

    $: goodExceedsEasy =
        $config.graduatingIntervalGood > $config.graduatingIntervalEasy
            ? tr.deckConfigGoodAboveEasy()
            : "";

    // svelte-ignore reactive_declaration_non_reactive_property
    $: insertionOrderRandom =
        $config.newCardInsertOrder == DeckConfig_Config_NewCardInsertOrder.RANDOM
            ? tr.deckConfigNewInsertionOrderRandomWithV3()
            : "";

    const settings = {
        learningSteps: {
            title: tr.deckConfigLearningSteps(),
            help: tr.deckConfigLearningStepsTooltip(),
            url: HelpPage.DeckOptions.learningSteps,
        },
        graduatingInterval: {
            title: tr.schedulingGraduatingInterval(),
            help: tr.deckConfigGraduatingIntervalTooltip(),
            url: HelpPage.DeckOptions.graduatingInterval,
            sched: HelpItemScheduler.SM2,
        },
        easyInterval: {
            title: tr.schedulingEasyInterval(),
            help: tr.deckConfigEasyIntervalTooltip(),
            url: HelpPage.DeckOptions.easyInterval,
            sched: HelpItemScheduler.SM2,
        },
        insertionOrder: {
            title: tr.deckConfigNewInsertionOrder(),
            help: tr.deckConfigNewInsertionOrderTooltip(),
            url: HelpPage.DeckOptions.insertionOrder,
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

<TitledContainer title={tr.schedulingNewCards()}>
    <HelpModal
        title={tr.schedulingNewCards()}
        url={HelpPage.DeckOptions.newCards}
        slot="tooltip"
        {helpSections}
        fsrs={$fsrs}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <StepsInputRow
                bind:value={$config.learnSteps}
                defaultValue={defaults.learnSteps}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("learningSteps"))}
                >
                    {settings.learningSteps.title}
                </SettingTitle>
            </StepsInputRow>
        </Item>

        <Item>
            <Warning warning={stepsTooLargeForFsrs} />
        </Item>

        {#if !$fsrs}
            <Item>
                <SpinBoxRow
                    bind:value={$config.graduatingIntervalGood}
                    defaultValue={defaults.graduatingIntervalGood}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("graduatingInterval"),
                            )}
                    >
                        {settings.graduatingInterval.title}
                    </SettingTitle>
                </SpinBoxRow>
            </Item>

            <Item>
                <Warning warning={stepsExceedGraduatingInterval} />
            </Item>

            <Item>
                <SpinBoxRow
                    bind:value={$config.graduatingIntervalEasy}
                    defaultValue={defaults.graduatingIntervalEasy}
                >
                    <SettingTitle
                        on:click={() =>
                            openHelpModal(
                                Object.keys(settings).indexOf("easyInterval"),
                            )}
                    >
                        {settings.easyInterval.title}
                    </SettingTitle>
                </SpinBoxRow>
            </Item>

            <Item>
                <Warning warning={goodExceedsEasy} />
            </Item>
        {/if}

        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardInsertOrder}
                defaultValue={defaults.newCardInsertOrder}
                choices={newInsertOrderChoices()}
                breakpoint={"md"}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("insertionOrder"))}
                >
                    {settings.insertionOrder.title}
                </SettingTitle>
            </EnumSelectorRow>
        </Item>

        <Item>
            <Warning warning={insertionOrderRandom} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
