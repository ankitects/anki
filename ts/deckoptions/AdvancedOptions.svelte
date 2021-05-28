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
    import RevertButton from "./RevertButton.svelte";
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
                bind:value={$config.maximumReviewInterval}
            />
            <RevertButton
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
            <SpinBoxFloat min={1.31} max={5} bind:value={$config.initialEase} />
            <RevertButton
                defaultValue={defaults.initialEase}
                bind:value={$config.initialEase}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingEasyBonus()}
            <HelpPopup html={marked(tr.deckConfigEasyBonusTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat min={1} max={3} bind:value={$config.easyMultiplier} />
            <RevertButton
                defaultValue={defaults.easyMultiplier}
                bind:value={$config.easyMultiplier}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingIntervalModifier()}
            <HelpPopup html={marked(tr.deckConfigIntervalModifierTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat min={0.5} max={2} bind:value={$config.intervalMultiplier} />
            <RevertButton
                defaultValue={defaults.intervalMultiplier}
                bind:value={$config.intervalMultiplier}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingHardInterval()}
            <HelpPopup html={marked(tr.deckConfigHardIntervalTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat min={0.5} max={1.3} bind:value={$config.hardMultiplier} />
            <RevertButton
                defaultValue={defaults.hardMultiplier}
                bind:value={$config.hardMultiplier}
            />
        </svelte:fragment>
    </ConfigEntry>

    <ConfigEntry>
        <span slot="left">
            {tr.schedulingNewInterval()}
            <HelpPopup html={marked(tr.deckConfigNewIntervalTooltip())} />
        </span>
        <svelte:fragment slot="right">
            <SpinBoxFloat min={0} max={1} bind:value={$config.lapseMultiplier} />
            <RevertButton
                defaultValue={defaults.lapseMultiplier}
                bind:value={$config.lapseMultiplier}
            />
        </svelte:fragment>
    </ConfigEntry>
</TitledContainer>
