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
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigTimerTitle()}>
    <Row>
        <Col size={7}>
            {tr.deckConfigMaximumAnswerSecs()}
            <HelpPopup html={marked(tr.deckConfigMaximumAnswerSecsTooltip())} />
        </Col>
        <Col size={5}>
            <SpinBox min={30} max={600} bind:value={$config.capAnswerTimeToSecs} />
            <RevertButton
                defaultValue={defaults.capAnswerTimeToSecs}
                bind:value={$config.capAnswerTimeToSecs}
            />
        </Col>
    </Row>

    <Row>
        <Col>
            <CheckBox bind:value={$config.showTimer}>
                {tr.schedulingShowAnswerTimer()}
                <HelpPopup html={marked(tr.deckConfigShowAnswerTimerTooltip())} />
            </CheckBox>
        </Col>
        <Col grow={false}>
            <RevertButton
                defaultValue={defaults.showTimer}
                bind:value={$config.showTimer}
            />
        </Col>
    </Row>
</TitledContainer>
