<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import ConfigEntry from "./ConfigEntry.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import SpinBox from "./SpinBox.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigAdvancedTitle()}>
    <ConfigEntry>
        <span slot="left">
            {tr.schedulingMaximumInterval()}
            <HelpPopup html={marked(tr.deckConfigMaximumIntervalTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBox
                min={1}
                max={365 * 100}
                defaultValue={defaults.maximumReviewInterval}
                bind:value={$config.maximumReviewInterval}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingStartingEase()}
            <HelpPopup html={marked(tr.deckConfigStartingEaseTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat
                min={1.31}
                max={5}
                defaultValue={defaults.initialEase}
                value={$config.initialEase}
                on:changed={(evt) => ($config.initialEase = evt.detail.value)}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingEasyBonus()}
            <HelpPopup html={marked(tr.deckConfigEasyBonusTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat
                min={1}
                max={3}
                defaultValue={defaults.easyMultiplier}
                value={$config.easyMultiplier}
                on:changed={(evt) => ($config.easyMultiplier = evt.detail.value)}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingIntervalModifier()}
            <HelpPopup html={marked(tr.deckConfigIntervalModifierTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat
                min={0.5}
                max={2}
                defaultValue={defaults.intervalMultiplier}
                value={$config.intervalMultiplier}
                on:changed={(evt) => ($config.intervalMultiplier = evt.detail.value)}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingHardInterval()}
            <HelpPopup html={marked(tr.deckConfigHardIntervalTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat
                min={0.5}
                max={1.3}
                defaultValue={defaults.hardMultiplier}
                value={$config.hardMultiplier}
                on:changed={(evt) => ($config.hardMultiplier = evt.detail.value)}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingNewInterval()}
            <HelpPopup html={tr.deckConfigNewIntervalTooltip()} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat
                min={0}
                max={1}
                defaultValue={defaults.lapseMultiplier}
                value={$config.lapseMultiplier}
                on:changed={(evt) => ($config.lapseMultiplier = evt.detail.value)}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>
