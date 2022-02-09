<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import type { Breakpoint } from "../components/types";
    import EnumSelector from "./EnumSelector.svelte";
    import RevertButton from "./RevertButton.svelte";
    import TooltipLabel from "./TooltipLabel.svelte";

    export let value: number;
    export let defaultValue: number;
    export let breakpoint: Breakpoint = "md";
    export let choices: string[];
    export let disabled: number[] = [];
    export let markdownTooltip: string;
</script>

<Row --cols={12}>
    <Col --col-size={7} {breakpoint}>
        <TooltipLabel {markdownTooltip}><slot /></TooltipLabel>
    </Col>
    <Col --col-size={5} {breakpoint}>
        <EnumSelector bind:value {choices} {disabled} />
        <RevertButton bind:value {defaultValue} />
    </Col>
</Row>
