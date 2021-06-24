<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import TooltipLabel from "./TooltipLabel.svelte";
    import RevertButton from "./RevertButton.svelte";
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";

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

<textarea bind:value />

<style>
    .text {
        min-height: 2em;
    }
    textarea {
        width: 100%;
        height: 10em;
        font-family: monospace;
    }
</style>
