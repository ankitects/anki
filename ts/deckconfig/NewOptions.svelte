<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import CheckBox from "./CheckBox.svelte";
    import StepsInput from "./StepsInput.svelte";
    import EnumSelector from "./EnumSelector.svelte";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;
    let config = state.currentConfig;
    let defaults = state.defaults;
    let parentLimits = state.parentLimits;

    const newOrderChoices = [
        tr.schedulingShowNewCardsInOrderAdded(),
        tr.schedulingShowNewCardsInRandomOrder(),
    ];

    let stepsExceedGraduatingInterval: boolean;
    $: {
        const lastLearnStepInDays = $config.learnSteps.length
            ? $config.learnSteps[$config.learnSteps.length - 1] / 60 / 24
            : 0;
        stepsExceedGraduatingInterval =
            lastLearnStepInDays > $config.graduatingIntervalGood;
    }

    $: goodExceedsEasy =
        $config.graduatingIntervalGood > $config.graduatingIntervalEasy;

    // fixme: change impl; support warning messages
    $: newCardsGreaterThanParent = $config.newPerDay > $parentLimits.newCards;
</script>

<div>
    <h2>New Cards</h2>

    <StepsInput
        label="Learning steps"
        subLabel="Learning steps, separated by spaces."
        warn={stepsExceedGraduatingInterval}
        defaultValue={defaults.learnSteps}
        value={$config.learnSteps}
        on:changed={(evt) => ($config.learnSteps = evt.detail.value)} />

    <EnumSelector
        label={tr.schedulingOrder()}
        subLabel=""
        choices={newOrderChoices}
        defaultValue={defaults.newCardOrder}
        bind:value={$config.newCardOrder} />

    <SpinBox
        label={tr.schedulingNewCardsday()}
        subLabel="The maximum number of new cards to introduce in a day."
        min={0}
        warn={newCardsGreaterThanParent}
        defaultValue={defaults.newPerDay}
        bind:value={$config.newPerDay} />

    <SpinBox
        label={tr.schedulingGraduatingInterval()}
        subLabel="Days to wait after answering Good on the last learning step."
        warn={stepsExceedGraduatingInterval || goodExceedsEasy}
        defaultValue={defaults.graduatingIntervalGood}
        bind:value={$config.graduatingIntervalGood} />

    <SpinBox
        label={tr.schedulingEasyInterval()}
        subLabel="Days to wait after answering Easy on the first learning step."
        warn={goodExceedsEasy}
        defaultValue={defaults.graduatingIntervalEasy}
        bind:value={$config.graduatingIntervalEasy} />

    <SpinBoxFloat
        label={tr.schedulingStartingEase()}
        subLabel="The default multiplier when a review is answered Good."
        min={1.31}
        max={5}
        defaultValue={defaults.initialEase}
        value={$config.initialEase}
        on:changed={(evt) => ($config.initialEase = evt.detail.value)} />

    <CheckBox
        label="Bury New"
        subLabel={tr.schedulingBuryRelatedNewCardsUntilThe()}
        defaultValue={defaults.buryNew}
        bind:value={$config.buryNew} />
</div>
