<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "./Row.svelte";
    import Col from "./Col.svelte";
    import Label from "./Label.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";
    import CheckBox from "./CheckBox.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let defaultValue: boolean;
    export let value: boolean = defaultValue;

    export let markdownTooltip: string | undefined = undefined;
</script>

<Row>
    <Col>
        <RevertButton bind:value {defaultValue} />
        <CheckBox bind:value
            >{#if markdownTooltip}<TooltipLabel {markdownTooltip}><slot /></TooltipLabel
                >{:else}<Label><slot /></Label>{/if}</CheckBox
        >
    </Col>
</Row>
