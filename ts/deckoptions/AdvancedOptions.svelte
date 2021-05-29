<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
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
    <Row>
        <Col size={7}>
            {tr.schedulingMaximumInterval()}
            <HelpPopup html={marked(tr.deckConfigMaximumIntervalTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBox
                min={1}
                max={365 * 100}
                bind:value={$config.maximumReviewInterval}
            />
            <RevertButton
                defaultValue={defaults.maximumReviewInterval}
                bind:value={$config.maximumReviewInterval}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingStartingEase()}
            <HelpPopup html={marked(tr.deckConfigStartingEaseTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBoxFloat min={1.31} max={5} bind:value={$config.initialEase} />
            <RevertButton
                defaultValue={defaults.initialEase}
                bind:value={$config.initialEase}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingEasyBonus()}
            <HelpPopup html={marked(tr.deckConfigEasyBonusTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBoxFloat min={1} max={3} bind:value={$config.easyMultiplier} />
            <RevertButton
                defaultValue={defaults.easyMultiplier}
                bind:value={$config.easyMultiplier}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingIntervalModifier()}
            <HelpPopup html={marked(tr.deckConfigIntervalModifierTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBoxFloat min={0.5} max={2} bind:value={$config.intervalMultiplier} />
            <RevertButton
                defaultValue={defaults.intervalMultiplier}
                bind:value={$config.intervalMultiplier}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingHardInterval()}
            <HelpPopup html={marked(tr.deckConfigHardIntervalTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBoxFloat min={0.5} max={1.3} bind:value={$config.hardMultiplier} />
            <RevertButton
                defaultValue={defaults.hardMultiplier}
                bind:value={$config.hardMultiplier}
            />
        </Col>
    </Row>

    <Row>
        <Col size={7}>
            {tr.schedulingNewInterval()}
            <HelpPopup html={marked(tr.deckConfigNewIntervalTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBoxFloat min={0} max={1} bind:value={$config.lapseMultiplier} />
            <RevertButton
                defaultValue={defaults.lapseMultiplier}
                bind:value={$config.lapseMultiplier}
            />
        </Col>
    </Row>
</TitledContainer>
