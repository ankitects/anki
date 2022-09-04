<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import RevertButton from "./RevertButton.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";

    export let value: string;
</script>

<Row>
    <Col>
        <div class="text">
            <TooltipLabel markdownTooltip={tr.deckConfigCustomSchedulingTooltip()}>
                {tr.deckConfigCustomScheduling()}:</TooltipLabel
            >
            <RevertButton bind:value defaultValue="" />
        </div>
    </Col>
</Row>

<textarea
    class="card-state-customizer form-control"
    bind:value
    spellcheck="false"
    autocapitalize="none"
/>

<style lang="scss">
    .text {
        min-height: 2em;
    }

    .card-state-customizer {
        color: var(--fg-default);
        background-color: var(--canvas-elevated);

        width: 100%;
        height: 10em;
        font-family: monospace;
    }

    @supports (-webkit-touch-callout: none) {
        // mobile compat
        .card-state-customizer {
            font-size: 16px;
            overflow-x: hidden;
        }
    }
</style>
