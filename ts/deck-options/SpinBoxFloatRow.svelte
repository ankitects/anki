<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "../components/Row.svelte";
    import Col from "../components/Col.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";
    import SpinBoxFloat from "./SpinBoxFloat.svelte";
    import RevertButton from "./RevertButton.svelte";

    export let value: any;
    export let defaultValue: any;
    export let min = 0;
    export let max: number | undefined = undefined;
    export let markdownTooltip: string;
</script>

<Row --cols={12}>
    <Col --col-size={7} breakpoint="sm">
        <TooltipLabel {markdownTooltip}><slot /></TooltipLabel>
    </Col>
    <Col --col-size={5} breakpoint="sm">
        <SpinBoxFloat bind:value {min} {max} />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
