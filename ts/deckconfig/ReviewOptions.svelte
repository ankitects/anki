<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "anki/i18n";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import CheckBox from "./CheckBox.svelte";
    import type { DeckConfigState } from "./lib";

    export let state: DeckConfigState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<div>
    <h2>Reviews</h2>

    <SpinBoxFloat
        label={tr.schedulingEasyBonus()}
        subLabel="Extra multiplier applied when answering Easy on a review card."
        min={1}
        max={3}
        defaultValue={defaults.easyMultiplier}
        value={$config.easyMultiplier}
        on:changed={(evt) => ($config.easyMultiplier = evt.detail.value)} />

    <SpinBoxFloat
        label={tr.schedulingIntervalModifier()}
        subLabel="Multiplier applied to all reviews."
        min={0.5}
        max={2}
        defaultValue={defaults.intervalMultiplier}
        value={$config.intervalMultiplier}
        on:changed={(evt) => ($config.intervalMultiplier = evt.detail.value)} />

    <SpinBox
        label={tr.schedulingMaximumInterval()}
        subLabel="The longest number of days a review card will wait."
        min={1}
        max={365 * 100}
        defaultValue={defaults.maximumReviewInterval}
        bind:value={$config.maximumReviewInterval} />

    <SpinBoxFloat
        label={tr.schedulingHardInterval()}
        subLabel="Multiplier applied to review interval when Hard is pressed."
        min={0.5}
        max={1.3}
        defaultValue={defaults.hardMultiplier}
        value={$config.hardMultiplier}
        on:changed={(evt) => ($config.hardMultiplier = evt.detail.value)} />

    <CheckBox
        subLabel={tr.schedulingBuryRelatedReviewsUntilTheNext()}
        defaultValue={defaults.buryReviews}
        bind:value={$config.buryReviews} />
</div>
