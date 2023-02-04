<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { anki } from "@tslib/backend_proto";
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
    import type { DeckOption } from "./types";

    export let state: DeckOptionsState;
    export let api: Record<string, never>;

    const config = state.currentConfig;
    const defaults = state.defaults;

    const burySiblings: DeckOption = {
        title: tr.deckConfigBurySiblings(),
        help: tr.deckConfigBuryTooltip(),
    };

    let modal: Modal;
    let carousel: Carousel;

    function openHelpModal(): void {
        modal.show();
        carousel.to(0);
    }

    const enum BuryMode {
        NONE,
        NEW,
        NEW_REVIEW,
        NEW_REVIEW_LEARNING,
    }

    const buryModeChoices = [
        tr.deckConfigNone(),
        tr.deckConfigOnlyNew(),
        tr.deckConfigOnlyNewReview(),
        tr.deckConfigNewReviewInterday(),
    ];

    function buryModeFromConfig(config: anki.deckconfig.DeckConfig.Config): BuryMode {
        // burying review cards is only allowed if also burying new cards
        const buryReviews = config.buryNew && config.buryReviews;
        // burying learning cards is only allowed if also burying review and new cards
        const buryInterdayLearning = buryReviews && config.buryInterdayLearning;
        return (
            Number(config.buryNew) + Number(buryReviews) + Number(buryInterdayLearning)
        );
    }

    function buryModeToConfig(mode: BuryMode) {
        $config.buryNew = mode >= 1;
        $config.buryReviews = mode >= 2;
        $config.buryInterdayLearning = mode >= 3;
    }

    let mode = buryModeFromConfig($config);
    $: buryModeToConfig(mode);
</script>

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <HelpModal
        title={tr.deckConfigBuryTitle()}
        url="https://docs.ankiweb.net/studying.html#siblings-and-burying"
        slot="tooltip"
        helpSections={[burySiblings]}
        on:mount={(e) => {
            modal = e.detail.modal;
            carousel = e.detail.carousel;
        }}
    />
    <DynamicallySlottable slotHost={Item} {api}>
        <Item>
            <EnumSelectorRow
                bind:value={mode}
                defaultValue={buryModeFromConfig(defaults)}
                choices={buryModeChoices}
            >
                <SettingTitle on:click={() => openHelpModal()}
                    >{tr.deckConfigBurySiblings()}</SettingTitle
                >
            </EnumSelectorRow>
        </Item>
    </DynamicallySlottable>
</TitledContainer>
