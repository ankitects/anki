<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { DeckConfig } from "@tslib/proto";
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

    const newInsertOrderChoices = [
        tr.deckConfigNewInsertionOrderSequential(),
        tr.deckConfigNewInsertionOrderRandom(),
    ];

    let stepsExceedGraduatingInterval: string;
    $: {
        const lastLearnStepInDays = $config.learnSteps.length
            ? $config.learnSteps[$config.learnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedGraduatingInterval =
            lastLearnStepInDays > $config.graduatingIntervalGood
                ? tr.deckConfigLearningStepAboveGraduatingInterval()
                : "";
    }

    $: goodExceedsEasy =
        $config.graduatingIntervalGood > $config.graduatingIntervalEasy
            ? tr.deckConfigGoodAboveEasy()
            : "";

    $: insertionOrderRandom =
        state.v3Scheduler &&
        $config.newCardInsertOrder ==
            DeckConfig.DeckConfig.Config.NewCardInsertOrder.NEW_CARD_INSERT_ORDER_RANDOM
            ? tr.deckConfigNewInsertionOrderRandomWithV3()
            : "";

    const settings = {
        learningSteps: {
            title: tr.deckConfigLearningSteps(),
            help: tr.deckConfigLearningStepsTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#learning-steps",
        },
        graduatingInterval: {
            title: tr.schedulingGraduatingInterval(),
            help: tr.deckConfigGraduatingIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#graduating-interval",
        },
        easyInterval: {
            title: tr.schedulingEasyInterval(),
            help: tr.deckConfigEasyIntervalTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#easy-interval",
        },
        insertionOrder: {
            title: tr.deckConfigNewInsertionOrder(),
            help: tr.deckConfigNewInsertionOrderTooltip(),
            url: "https://docs.ankiweb.net/deck-options.html#insertion-order",
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

<TitledContainer title={tr.schedulingNewCards()}>
    <HelpModal
        title={tr.schedulingNewCards()}
        url="https://docs.ankiweb.net/deck-options.html#new-cards"
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
                bind:value={$config.learnSteps}
                defaultValue={defaults.learnSteps}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("learningSteps"))}
                    >{settings.learningSteps.title}</SettingTitle
                >
            </StepsInputRow>
        </Item>

        <Item>
            <SpinBoxRow
                bind:value={$config.graduatingIntervalGood}
                defaultValue={defaults.graduatingIntervalGood}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(
                            Object.keys(settings).indexOf("graduatingInterval"),
                        )}>{settings.graduatingInterval.title}</SettingTitle
                >
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
                        openHelpModal(Object.keys(settings).indexOf("easyInterval"))}
                    >{settings.easyInterval.title}</SettingTitle
                >
            </SpinBoxRow>
        </Item>

        <Item>
            <Warning warning={goodExceedsEasy} />
        </Item>

        <Item>
            <EnumSelectorRow
                bind:value={$config.newCardInsertOrder}
                defaultValue={defaults.newCardInsertOrder}
                choices={newInsertOrderChoices}
                breakpoint={"md"}
            >
                <SettingTitle
                    on:click={() =>
                        openHelpModal(Object.keys(settings).indexOf("insertionOrder"))}
                    >{settings.insertionOrder.title}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>

        <Item>
            <Warning warning={insertionOrderRandom} />
        </Item>
    </DynamicallySlottable>
</TitledContainer>
