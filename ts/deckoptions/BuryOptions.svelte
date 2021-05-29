<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import marked from "marked";
    import TitledContainer from "./TitledContainer.svelte";
    import Col from "./Col.svelte";
    import Row from "./Row.svelte";
    import HelpPopup from "./HelpPopup.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";
    import type { DeckOptionsState } from "./lib";

    export let state: DeckOptionsState;
    let config = state.currentConfig;
    let defaults = state.defaults;
</script>

<TitledContainer title={tr.deckConfigBuryTitle()}>
    <Row>
        <Col>
            <CheckBox bind:value={$config.buryNew}>
                {tr.deckConfigBuryNewSiblings()}
                <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
            </CheckBox>
        </Col>
        <Col grow={false}>
            <RevertButton
                defaultValue={defaults.buryNew}
                bind:value={$config.buryNew}
            />
        </Col>
    </Row>

    <Row>
        <Col>
            <CheckBox bind:value={$config.buryReviews}>
                {tr.deckConfigBuryReviewSiblings()}
                <HelpPopup html={marked(tr.deckConfigBuryTooltip())} />
            </CheckBox>
        </Col>
        <Col grow={false}>
            <RevertButton
                defaultValue={defaults.buryReviews}
                bind:value={$config.buryReviews}
            />
        </Col>
    </Row>
</TitledContainer>
