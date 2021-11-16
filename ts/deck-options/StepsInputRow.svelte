<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";
    import StepsInput from "./StepsInput.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: any;
    export let defaultValue: any;
    export let markdownTooltip: string;
</script>

<Row --cols={12}>
    <Col --col-size={7}>
        <TooltipLabel {markdownTooltip}><slot /></TooltipLabel>
    </Col>
    <Col --col-size={5}>
        <StepsInput bind:value />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
